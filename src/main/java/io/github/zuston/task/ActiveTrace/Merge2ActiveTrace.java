package io.github.zuston.task.ActiveTrace;

import io.github.zuston.basic.Trace.OriginalTraceRecordParser;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Iterator;
import java.util.Random;


enum COUNTER {
    MERGE_ERROR,

    NONE_DISTINCTED_ACTIVE_RECORD_COUNT,
    DISTINCTED_ACTIVE_RECORD_COUNT
}

/**
 * Created by zuston on 2018/1/15.
 */
public class Merge2ActiveTrace extends Configured implements Tool {

    public static final double believeValue = 1.96;

    // 已经过滤过的 trace 记录
    static class FilteredTraceMapper extends Mapper<LongWritable, Text, FilterPair, Text>{

        private OriginalTraceRecordParser parser = new OriginalTraceRecordParser();

        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String [] splitArr = value.toString().split("\\t+");
            parser.parser(splitArr[1]);
            String scanTime = parser.getSCAN_TIME();
            String [] arr = scanTime.split("\\s+")[0].split("-");
            // 修复匹配不到预测时间的bug
            String frontSuffix = arr[0]+"-"+arr[1];
            context.write(new FilterPair(splitArr[0]+"#"+frontSuffix,"1"),value);
        }
    }

    // 已经预测的时间
    static class PredictTimeMapper extends Mapper<LongWritable, Text, FilterPair, Text>{
        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String [] splitArr = value.toString().split("\\s+");
            context.write(new FilterPair(splitArr[0],"0"), new Text(splitArr[1]));
        }
    }

    // 分区
    static class MergePartitioner extends Partitioner<FilterPair, Text>{

        public int getPartition(FilterPair key, Text value, int i) {
            return ( key.getFirst().hashCode() & Integer.MAX_VALUE ) % i;
        }
    }

    // 分组
    static class MergeGrouper extends WritableComparator{

        public MergeGrouper(){
            super(FilterPair.class, true);
        }

        public int compare(WritableComparable a, WritableComparable b) {
            FilterPair aP = (FilterPair) a;
            FilterPair bP = (FilterPair) b;
            return aP.getFirst().compareTo(bP.getFirst());
        }
    }


    static class Merge2ActiveTraceReducer extends Reducer<FilterPair, Text, Text, Text>{

        Random random = new Random();

        @Override
        public void reduce(FilterPair key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            Iterator<Text> iterator = values.iterator();
            Text predictData = iterator.next();
            // TODO: 2018/1/24
            if (predictData.toString().split("#").length!=1){
                context.getCounter("Merge2ActiveTrace","MERGE_ERROR").increment(1);
                return;
            }
            String [] array = predictData.toString().split(":");
            //  均值，方差，样本总量
            String time = array[0];
            String count = array[2];
            String var = array[1];

            double predictTime = predictAlg(time,count,var);

            while (iterator.hasNext()){
                Text value = iterator.next();
                if (value.toString().split("#").length==1){
                    context.getCounter("Merge2ActiveTrace","MERGE_ERROR_To_End").increment(1);
                    return;
                }
                // 加上一个随机扰动
                Integer randomDisturbance = random.nextInt(10);
                context.write(new Text(value.toString()+"#"+(predictTime+randomDisturbance)),null);
            }
        }

        // 评估预测，加上一个随机扰动
        private double predictAlg(String time, String count, String var) {
            Integer randomDisturbance = random.nextInt(10);
//            return Double.valueOf(time) + believeValue * Math.sqrt(Double.parseDouble(var)) / Math.sqrt(Double.parseDouble(count)) + randomDisturbance;
            return Double.valueOf(time) + believeValue * Math.sqrt(Double.parseDouble(var));
        }
    }




    public int run(String[] strings) throws Exception {
        Job job = new Job(this.getConf(),"merge2ActiveTrace");
        job.setJarByClass(Merge2ActiveTrace.class);

        Path FilterTimeActiveRecordPath = new Path(strings[0]);
        Path PredictTimeRecord = new Path(strings[1]);
        Path output = new Path(strings[2]);

        MultipleInputs.addInputPath(job, FilterTimeActiveRecordPath, TextInputFormat.class, FilteredTraceMapper.class);
        MultipleInputs.addInputPath(job, PredictTimeRecord, TextInputFormat.class, PredictTimeMapper.class);

        FileOutputFormat.setOutputPath(job, output);

        job.setMapOutputKeyClass(FilterPair.class);
        job.setMapOutputValueClass(Text.class);

        job.setPartitionerClass(MergePartitioner.class);

        job.setGroupingComparatorClass(MergeGrouper.class);

        job.setReducerClass(Merge2ActiveTraceReducer.class);

        job.setNumReduceTasks(Integer.parseInt(strings[3]));

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        return job.waitForCompletion(true) ? 0 : 1;
    }
}

class FilterPair implements WritableComparable<FilterPair>{
    private Text first;
    private Text second;

    public FilterPair(){
        set(new Text(),new Text());
    }

    public FilterPair(String first, String second){
        set(new Text(first), new Text(second));
    }

    public FilterPair(Text first, Text second){
        set(first, second);
    }

    public void set(Text first, Text second){
        this.first = first;
        this.second = second;
    }

    public Text getFirst() {
        return first;
    }

    public Text getSecond() {
        return second;
    }

    public int compareTo(FilterPair o) {
        int cmp = first.compareTo(o.getFirst());
        if (cmp != 0)   return cmp;
        return second.compareTo(o.getSecond());
    }

    public void write(DataOutput dataOutput) throws IOException {
        first.write(dataOutput);
        second.write(dataOutput);
    }

    public void readFields(DataInput dataInput) throws IOException {
        first.readFields(dataInput);
        second.readFields(dataInput);
    }

    public String toString(){
        return first + "\t" + second;
    }

}


