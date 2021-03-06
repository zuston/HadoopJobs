package io.github.zuston.webService.Service;

import com.google.gson.Gson;
import io.github.zuston.util.ListTool;
import io.github.zuston.util.RedisTool;
import io.github.zuston.webService.Pojo.Site2SitePojo;
import io.github.zuston.webService.Pojo.TraceInfoPojo;
import io.github.zuston.webService.Pojo.targetInfoPojo;
import io.github.zuston.webService.Tool.HBaseTool;
import io.github.zuston.webService.Tool.MysqlTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

/**
 * Created by zuston on 2018/1/18.
 */
@Service
public class ApiService {

    public static final String DATE_KEY = "TASK_PROCESS_CACHE_DATE";

    @Autowired
    private Gson gson;

    public static final String VALIDATE_TABLE_NAME = "validate";

    public static final String ACTIVE_TRACE = "activeTrace";

    public static final String ACTIVE_TRACE_OUT = "ActiveRecord_Out";
    public static final String ACTIVE_TRACE_IN = "ActiveRecord_In";

    public String siteTraceInfo_HBase(){
        List<Site2SitePojo> reslist = new ArrayList<Site2SitePojo>();
        String resJson = "";
        try {
            List<HashMap<String,String>> site2siteList = HBaseTool.QueryAll(VALIDATE_TABLE_NAME);
            for (HashMap<String, String> hm : site2siteList){
                String site2siteLine = hm.get("rowkey");
                String startName = site2siteLine.split("#")[0];
                String endName = site2siteLine.split("#")[1];
                int total = Integer.parseInt(hm.get("total"));
                int abnormal = Integer.parseInt(hm.get("abnormal"));
                Site2SitePojo pojo = new Site2SitePojo(startName, endName, total, abnormal);
                reslist.add(pojo);
            }
            resJson = gson.toJson(reslist);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return resJson;
    }

    public String siteTraceInfo(){
        try {
            List<Site2SitePojo> reslist = MysqlTool.QueryAll(VALIDATE_TABLE_NAME);
            String resJson = gson.toJson(reslist);
            return  resJson;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    // 查找出当前站点的在途订单，再查询订单详情。

    /**
     *
     * @param siteId
     * @param size
     * @param tag  1 == out , 2 == in
     * @return
     */
    public String siteInfo(long siteId, int size, int tag) throws SQLException {
        List<List<TraceInfoPojo>> reslist = MysqlTool.Query(ACTIVE_TRACE, siteId, size, tag);
        return gson.toJson(reslist);
    }


    public String siteInfo_HBase(long siteId, int size, int tag, int page) throws IOException {
        String tableName = "ActiveRecord";

        String siteIndexName = null;
        if (tag==1) siteIndexName = "siteIndex_Out";
        if (tag==2) siteIndexName = "siteIndex_In";
        if (tag==3) siteIndexName = "delayIndex";

        String ewbIndexName = "ewbIndex";

        List<String> ewbList = HBaseTool.GetBySiteId(siteIndexName, String.valueOf(siteId), size, page);

        List<List<TraceInfoPojo>> reslist = new ArrayList<List<TraceInfoPojo>>();

        List<HashMap<String,String>> idList = HBaseTool.GetIndex(ewbIndexName, ListTool.list2arr(ewbList));

        for (HashMap<String,String> hashMap : idList){
            List<String> rowKeyList = new ArrayList<String>();
            for (Map.Entry<String, String> entry : hashMap.entrySet()){
                String ewbNo = entry.getKey();
                String [] siteArr = entry.getValue().split("%");
                for (String site : siteArr){
                    rowKeyList.add(ewbNo + "#" + site);
                }
            }
            List<TraceInfoPojo> pojos = HBaseTool.BatchGet(tableName, ListTool.list2arr(rowKeyList));
            reslist.add(pojos);
        }
        return gson.toJson(reslist);
    }


    public String test() throws IOException {
        HBaseTool.get();
        return "";
    }

    public String traceInfo(String id) throws IOException {
        List<HashMap<String,String>> idList = HBaseTool.GetIndex("ewbIndex", new String[]{String.valueOf(id)});
        List<String> rowKeyList = new ArrayList<String>();

        for (Map.Entry<String, String> entry : idList.get(0).entrySet()){
            String ewbNo = entry.getKey();
            String [] siteArr = entry.getValue().split("%");
            for (String site : siteArr){
                rowKeyList.add(ewbNo + "#" + site);
            }
        }
        List<TraceInfoPojo> pojos = HBaseTool.BatchGet("ActiveRecord", ListTool.list2arr(rowKeyList));
        Collections.sort(pojos, new Comparator<TraceInfoPojo>() {
            @Override
            public int compare(TraceInfoPojo o1, TraceInfoPojo o2) {
                return (int) (Timestamp.valueOf(o1.getSCAN_TIME()).getTime() - Timestamp.valueOf(o2.getSCAN_TIME()).getTime());
            }
        });
        return gson.toJson(pojos);
    }

    public String targetInfo(long siteId) throws IOException, SQLException {
        targetInfoPojo pojo = new targetInfoPojo();
        String date = RedisTool.getString(DATE_KEY);
        if (date!=null) pojo.settingData = date;
        else return null;
        List<Long> ewbCountList = HBaseTool.GetEwbCount(String.valueOf(siteId));
//        List<String> validateList = MysqlTool.GetTargetInfo(String.valueOf(siteId));
        pojo.traveCount = ewbCountList.get(2);
        pojo.outCount = String.valueOf(ewbCountList.get(0));
        pojo.inCount  = String.valueOf(ewbCountList.get(1));
//        pojo.delayCount = validateList.get(2);
        pojo.delayCount = HBaseTool.GetDelayCount(String.valueOf(siteId));
        return gson.toJson(pojo);
    }

    public String inTraveCount() throws SQLException {
        return MysqlTool.GetIntraveCount();
    }

    public String linkSites(String siteId) throws SQLException {
        List<List<String>> list = MysqlTool.GetLinkSites(siteId);
        return gson.toJson(list);
    }
}
