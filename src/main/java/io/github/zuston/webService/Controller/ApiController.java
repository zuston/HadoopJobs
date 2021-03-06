package io.github.zuston.webService.Controller;

import io.github.zuston.webService.Service.ApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Created by zuston on 2018/1/18.
 */

@RestController
@RequestMapping("/atcal")
public class ApiController {

    @Autowired
    private ApiService apiService;

    @RequestMapping(value = "/currentinfo",method = RequestMethod.GET)
    public String siteTraceInfo(){
        return apiService.siteTraceInfo();
    }

    @RequestMapping(value = "/siteinfo", method = RequestMethod.GET)
    public String siteInfo(@RequestParam("siteId")long siteId, @RequestParam("size")int size, @RequestParam("tag")int tag, @RequestParam("page")int page) throws SQLException, IOException {
        return apiService.siteInfo_HBase(siteId, size, tag, page);
    }

    @RequestMapping(value = "/targetinfo", method = RequestMethod.GET)
    public String targetInfo(@RequestParam("siteId")long siteId) throws IOException, SQLException {
        return apiService.targetInfo(siteId);
    }

    @RequestMapping(value = "/inTraveCount", method = RequestMethod.GET)
    public String inTraveCount() throws SQLException {
        return apiService.inTraveCount();
    }

    @RequestMapping(value = "/linkSites", method = RequestMethod.GET)
    public String linkSites(@RequestParam("siteId")String siteId) throws SQLException {
        return apiService.linkSites(siteId);
    }

    @RequestMapping(value = "/traceInfo", method = RequestMethod.GET)
    public String test(@RequestParam("ewb")String id) throws IOException {
        return apiService.traceInfo(id);
    }
}
