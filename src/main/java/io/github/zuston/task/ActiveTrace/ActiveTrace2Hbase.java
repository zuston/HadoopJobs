package io.github.zuston.task.ActiveTrace;

import io.github.zuston.util.*;
import io.github.zuston.basic.Trace.OriginalTraceRecordParser;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Random;

/**
 * Created by zuston on 2018/1/17.
 */
public class ActiveTrace2Hbase extends Configured implements Tool {

    public static Logger logger = LoggerFactory.getLogger(ActiveTrace2Hbase.class);


    static final byte[] COLUMN_FAMILIY_INFO = Bytes.toBytes("info");

    public static OriginalTraceRecordParser parser = new OriginalTraceRecordParser();

    public static Field[] fields = parser.getClass().getDeclaredFields();

    public static final String tableTag = "tableTag";
    public static final String tableTagIn = "in";

    static class SampleMapper extends Mapper<LongWritable, Text, Text, Text>{

        Random random = new Random();

        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String [] record = value.toString().split("\\t+");
            if (record[0].split("#").length<=1)    {
                context.getCounter("ActiveTraceSample","header error").increment(1);
                return;
            }
            if (random.nextInt(50)!=0)  return;

            String originalSqlRecord = record[1].substring(0, record[1].lastIndexOf("#"));
//            String rowKeyId = record[0].split("#")[0];
//            if (context.getConfiguration().get(tableTag).equals(tableTagIn)){
//                rowKeyId = record[0].split("#")[1];
//            }
//            String startId = record[0].split("#")[0];
//            String endId = record[0].split("#")[1];
//            if (!parser.parser(originalSqlRecord))  return;
//            String rowKeyComponent = String.format("%s#%s#%s", parser.getEWB_NO(), startId, endId);

            if (!parser.parser(originalSqlRecord))  return;

            if (
                    parser.getEWB_NO()==null || parser.getTRACE_ID()==null
                    || parser.getEWB_NO().equals("")
                    || parser.getTRACE_ID().equals("")
                    )
                return;

            // 直接 rowKEY : 订单号#traceId
            String rowKeyComponent = String.format("%s#%s", parser.getEWB_NO(), parser.getTRACE_ID());

            context.write(new Text(rowKeyComponent),new Text(""));
        }
    }

    static class SampleReducer extends Reducer<Text, Text, Text, Text>{
        @Override
        public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            context.write(key, null);
        }
    }


    static class AtMapper extends Mapper<LongWritable, Text, ImmutableBytesWritable, Put> {


        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String [] record = value.toString().split("\\t+");
            if (record[0].split("#").length<=1)    {
                context.getCounter("ActiveTrace2Hbase","header error").increment(1);
                return;
            }
            // 重新定义的地方
            String originalSqlRecord = record[1].substring(0, record[1].lastIndexOf("#"));
            String predictTime = record[1].substring(record[1].lastIndexOf("#")+1,record[1].length());

            String rowKeyId = record[0].split("#")[0];
            if (context.getConfiguration().get(tableTag).equals(tableTagIn)){
                rowKeyId = record[0].split("#")[1];
            }

            if (!parser.parser(originalSqlRecord)){
                return;
            }

            if (parser.getEWB_NO().equals("") || parser.getSITE_ID().equals(""))  {
                return;
            }
            // 出发地+订单号  out
            // 目的地+订单号  in
//            String startId = record[0].split("#")[0];
            String endId = record[0].split("#")[1];
//            String rowKeyComponent = String.format("%s#%s#%s", parser.getEWB_NO(), startId, endId);

            String rowKeyComponent = String.format("%s#%s", parser.getEWB_NO(), parser.getTRACE_ID());

            byte[] rowKey = Bytes.toBytes(rowKeyComponent);
            Put condition = new Put(rowKey);

            // 待查，是否对性能有巨大影响
            for (Field field : fields){
                try {
                    field.setAccessible(true);
                    String fieldValue = (String) field.get(parser);
                    String fieldName = field.getName();
                    // 填充 end_id
                    if (fieldValue.equals("") && fieldName.equals("DEST_SITE_ID") && endId!=null)  fieldValue=endId;
                    if (fieldValue.equals(""))  continue;
                    condition.add(COLUMN_FAMILIY_INFO,Bytes.toBytes(fieldName),Bytes.toBytes(fieldValue));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
            condition.add(COLUMN_FAMILIY_INFO,Bytes.toBytes("PREDICT_TIME"),Bytes.toBytes(predictTime));
            context.write(new ImmutableBytesWritable(rowKey), condition);
        }
    }

    /**
     *
     * @param strings
     * @return
     * @throws Exception
     *
     * 输入文件
     * 输出文件
     * 表名
     *
     */
    public int run(String[] strings) throws Exception {

        transferConf(strings[2]);

        String samplePath = "/temp/B_activeRecord_Sample_" + strings[2];

        String [] sampleOpts = new String[]{
                strings[0],
                samplePath
        };
        sample(sampleOpts);

        String sampleFile = samplePath + "/part-r-00000";
        String sampleFileLine = ShellTool.exec("hdfs dfs -cat "+sampleFile +" | wc -l");
        logger.error("采样文件行数 ："+sampleFileLine.split("\\n")[0]);

//                * 参数1：分区文件
//                * 参数2：表名
//                * 参数3：分区数目
//                * 参数4：分区表的行数
        String createHBaseOpts [] = new String[]{
                sampleFile,
                strings[2],
                "20",
                sampleFileLine.split("\\n")[0]
        };

        ToolRunner.run(new HbaseTool(),createHBaseOpts);

        logger.error("建表成功");

        HdfsTool.deleteDir(samplePath);

        // 生成数据
        HTable table = null;
        try {
            Job job = JobGenerator.HbaseQuickImportJobGnerator(this, this.getConf(),strings, table);
            job.setJobName("ActiveTrace2Hbase-"+strings[2]);
            job.setMapperClass(AtMapper.class);
            job.getConfiguration().setStrings("mapreduce.reduce.shuffle.input.buffer.percent", "0.1");

            if (job.waitForCompletion(true)){
                logger.error("生成 hfile 成功");
                // bulkload
                ToolRunner.run(new BulkLoadTool(),new String[]{strings[2],strings[1]});
                logger.error("导入成功");

            }else{
                logger.error("生成 hfile 失败");
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if (table!=null)    table.close();
        }
        // 为了演示所用
        outputNotifyInfo();
        return 0;
    }

    private void outputNotifyInfo() {
        String outLine = StringTool.component('=',30);
        System.out.println(outLine);
        System.out.println("各站点间时间预测分析已完成");
        System.out.println(outLine);
    }

    private void transferConf(String tableName) {
        if (tableName.toLowerCase().contains(tableTagIn)){
            this.getConf().set(tableTag,tableTagIn);
            return;
        }
        this.getConf().set(tableTag, "out");
    }


    private void sample(String [] opts) throws Exception {
        Job job = new Job(this.getConf());
        job.setJarByClass(ActiveTrace2Hbase.class);
        job.setJobName("atSampleCollector");
        job.setMapperClass(SampleMapper.class);
        job.setReducerClass(SampleReducer.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job, new Path(opts[0]));
        FileOutputFormat.setOutputPath(job, new Path(opts[1]));

        job.waitForCompletion(true);
    }

}
