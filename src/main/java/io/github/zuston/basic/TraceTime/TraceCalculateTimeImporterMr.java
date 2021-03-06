package io.github.zuston.basic.TraceTime;

import io.github.zuston.util.JobGenerator;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.util.Tool;

import java.io.IOException;
import java.util.HashMap;

/**
 * Created by zuston on 2017/12/22.
 */
public class TraceCalculateTimeImporterMr extends Configured implements Tool {
    // create "TraceTime_Out","January","February","March","April","May","June","July","August","September","October","November","December"
    public static final byte[] HS_OPT_TRACE_COLUMN_FAMILY_JAN = Bytes.toBytes("January");
    public static final byte[] HS_OPT_TRACE_COLUMN_FAMILY_FEB = Bytes.toBytes("February");
    public static final byte[] HS_OPT_TRACE_COLUMN_FAMILY_MAR = Bytes.toBytes("March");
    public static final byte[] HS_OPT_TRACE_COLUMN_FAMILY_APR = Bytes.toBytes("April");
    public static final byte[] HS_OPT_TRACE_COLUMN_FAMILY_MAY = Bytes.toBytes("May");
    public static final byte[] HS_OPT_TRACE_COLUMN_FAMILY_JUNE = Bytes.toBytes("June");
    public static final byte[] HS_OPT_TRACE_COLUMN_FAMILY_JULY = Bytes.toBytes("July");
    public static final byte[] HS_OPT_TRACE_COLUMN_FAMILY_AUG = Bytes.toBytes("August");
    public static final byte[] HS_OPT_TRACE_COLUMN_FAMILY_SEPT = Bytes.toBytes("September");
    public static final byte[] HS_OPT_TRACE_COLUMN_FAMILY_OCT = Bytes.toBytes("October");
    public static final byte[] HS_OPT_TRACE_COLUMN_FAMILY_NOV = Bytes.toBytes("November");
    public static final byte[] HS_OPT_TRACE_COLUMN_FAMILY_DEC = Bytes.toBytes("December");

    public static HashMap<String, byte[]> MAPPER = new HashMap<String, byte[]>();
    static {
        MAPPER.put("01",HS_OPT_TRACE_COLUMN_FAMILY_JAN);
        MAPPER.put("02",HS_OPT_TRACE_COLUMN_FAMILY_FEB);
        MAPPER.put("03",HS_OPT_TRACE_COLUMN_FAMILY_MAR);
        MAPPER.put("04",HS_OPT_TRACE_COLUMN_FAMILY_APR);
        MAPPER.put("05",HS_OPT_TRACE_COLUMN_FAMILY_MAY);
        MAPPER.put("06",HS_OPT_TRACE_COLUMN_FAMILY_JUNE);
        MAPPER.put("07",HS_OPT_TRACE_COLUMN_FAMILY_JULY);
        MAPPER.put("08",HS_OPT_TRACE_COLUMN_FAMILY_AUG);
        MAPPER.put("09",HS_OPT_TRACE_COLUMN_FAMILY_SEPT);
        MAPPER.put("10",HS_OPT_TRACE_COLUMN_FAMILY_OCT);
        MAPPER.put("11",HS_OPT_TRACE_COLUMN_FAMILY_NOV);
        MAPPER.put("12",HS_OPT_TRACE_COLUMN_FAMILY_DEC);

    }

    public static boolean site_in_tag = false;

    /**
     *
     *     site_name ------> sites:A B C D E F
     */
    static class HS_OPT_TRACE_ImporterMapper extends Mapper<LongWritable, Text, ImmutableBytesWritable, Put>{

        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String [] arrs = value.toString().split("\\s+");
            String statisticsInfo [] = arrs[1].split(":");

            if (arrs[0].split("#").length!=3){
                return;
            }

            String startSite = arrs[0].split("#")[0];
            String endSite = arrs[0].split("#")[1];
            // 进出表的入库
            if (site_in_tag){
                String temp = startSite;
                startSite = endSite;
                endSite = temp;
            }
            String month = arrs[0].split("#")[2].split("-")[1];

            if (startSite.equals("") || endSite.equals("") || month.equals(""))
                return;

            byte[] rowKey = Bytes.toBytes(startSite);
            Put putCondition = new Put(rowKey);
            putCondition.add(
                    MAPPER.get(month),
                    Bytes.toBytes(endSite+"_AVG"),
                    Bytes.toBytes(statisticsInfo[0])
            );

            putCondition.add(
                    MAPPER.get(month),
                    Bytes.toBytes(endSite+"_VAR"),
                    Bytes.toBytes(statisticsInfo[1])
            );

            putCondition.add(
                    MAPPER.get(month),
                    Bytes.toBytes(endSite+"_SAMPLE"),
                    Bytes.toBytes(statisticsInfo[2])
            );

            context.write(new ImmutableBytesWritable(rowKey), putCondition);
        }
    }

    public int run(String[] strings) throws Exception {
        this.getConf().set("hbase.master", "master:60000");
        this.getConf().set("hbase.zookeeper.quorum",
                "slave4,slave2,slave3");

        Job job = JobGenerator.HbaseImportJobGnerator(this, this.getConf(),strings);
        job.setJobName("TraceTime2Hbase");
        job.setMapperClass(HS_OPT_TRACE_ImporterMapper.class);
        if (strings[1].toLowerCase().contains("in")){
            site_in_tag = true;
        }
        return job.waitForCompletion(true) ? 0 : 1;
    }
}
