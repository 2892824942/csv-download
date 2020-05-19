package com.learn.csvdownload.controller;

import com.learn.csvdownload.core.CsvUtil;
import com.learn.csvdownload.entity.Worker;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by suyouliang .
 */
@Controller
@RequestMapping("/api/test/v1")
public class DemoController {

    @GetMapping("/download-csv-normal")
    public ResponseEntity<Resource> downloadCsvNormal() {
        List<Worker> workers = initData();
        return CsvUtil.sendDataStream(workers, "have_id_card", Worker.class);

    }

    @GetMapping("/download-csv-group")
    public ResponseEntity<Resource> downLoadCsvGroup() {
        List<Worker> workers = initData();
        return CsvUtil.sendDataStream(workers, "no_id_card", "myGroup", Worker.class);

    }

    @GetMapping("/download-empty")
    @ResponseBody
    public ResponseEntity<Resource> downLoadEmpty() {
        return CsvUtil.sendDataStream(null, "empty_data", "no_id_card", Worker.class);

    }

    private List<Worker> initData() {
        List<Worker> dataList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Worker worker = new Worker();
            //这里测试出现与CSV逻辑符号","冲突时是否能正常显示
            worker.setName("张,\r\n" + i);
            worker.setAge(10 + i);
            worker.setSex(i % 2 == 0 ? 0 : 1);
            worker.setBirthDay(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 10 * i));
            //这里测试出现与CSV逻辑符号"\r\n"冲突时是否能正常显示
            worker.setIdCard("345454198" + i + "xxxxxxx");
            dataList.add(worker);
        }
        return dataList;

    }

}