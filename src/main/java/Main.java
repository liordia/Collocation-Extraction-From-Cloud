import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.jobcontrol.ControlledJob;
import org.apache.hadoop.mapreduce.lib.jobcontrol.JobControl;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.charset.StandardCharsets;


public class Main {
    public static void main(String[] args) throws Exception {

        Configuration conf = new Configuration();
        String stopWords = "";
        String stopWordsPath = "-stopwords.txt";
        String inputPath = "s3://datasets.elasticmapreduce/ngrams/books/20090715/";
        String bucketPath = "s3://collocation-ds/";
        Region region = Region.US_EAST_1;
        S3Client s3 = S3Client.builder()
                .region(region)
                .build();

        if (args[0].equals("heb")){
            stopWordsPath = "heb" + stopWordsPath;
            inputPath = inputPath + "heb-all/2gram/data";
        } else {
            stopWordsPath = "eng" + stopWordsPath;
            inputPath = inputPath + "eng-1M/2gram/data";
        }

        try {
            GetObjectRequest objectRequest = GetObjectRequest
                    .builder()
                    .key(stopWordsPath)
                    .bucket("collocation-ds")
                    .build();
            ResponseBytes<GetObjectResponse> objectBytes = s3.getObjectAsBytes(objectRequest);
            byte[] data = objectBytes.asByteArray();
            stopWords = new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println();
        }

        conf.set("stop.words", stopWords);
        conf.set("lang", args[0]);
        conf.set("fs.hdfs.impl",
                org.apache.hadoop.hdfs.DistributedFileSystem.class.getName()
        );
        conf.set("fs.file.impl",
                org.apache.hadoop.fs.LocalFileSystem.class.getName()
        );
        Job job = Job.getInstance(conf, "Collocation-MapReduce1");
        job.setJarByClass(MapReducer1.class);
        job.setMapperClass(MapReducer1.TokenizerMapper.class);
        job.setMapOutputKeyClass(WordAndYear.class);
        job.setPartitionerClass(MapReducer1.DecadePartitioner1.class);
        job.setMapOutputValueClass(DoubleWritable.class);
        job.setReducerClass(MapReducer1.IntSumReducer.class);
        job.setOutputKeyClass(WordAndYear.class);
        job.setOutputValueClass(DoubleWritable.class);
        job.setInputFormatClass(SequenceFileInputFormat.class);
        job.setNumReduceTasks(32);

        FileInputFormat.addInputPath(job, new Path(inputPath));
        FileOutputFormat.setOutputPath(job, new Path(bucketPath + args[0] + "output_1"));

        Configuration conf2 = new Configuration();
        conf2.set("fs.hdfs.impl",
                org.apache.hadoop.hdfs.DistributedFileSystem.class.getName()
        );
        conf2.set("fs.file.impl",
                org.apache.hadoop.fs.LocalFileSystem.class.getName()
        );
        Job job2 = Job.getInstance(conf2, "Collocation-MapReduce2");
        job2.setJarByClass(MapReducer2.class);
        job2.setMapperClass(MapReducer2.Mapper2.class);
        job2.setMapOutputKeyClass(WordAndCounter.class);
        job2.setMapOutputValueClass(DoubleWritable.class);
        job2.setNumReduceTasks(32);
        job2.setPartitionerClass(MapReducer2.DecadePartitioner2.class);
        job2.setReducerClass(MapReducer2.Reducer2.class);
        job2.setOutputKeyClass(WordAndCounter.class);
        job2.setOutputValueClass(DoubleWritable.class);
        FileInputFormat.addInputPath(job2, new Path("s3://thecoolbucketthatismine/input/"));
        FileOutputFormat.setOutputPath(job2, new Path(bucketPath+ args[0]  + "output_2"));


        Job job3 = Job.getInstance(conf2, "Collocation-MapReduce3");
        job3.setJarByClass(MapReducer3.class);
        job3.setMapperClass(MapReducer3.Mapper3.class);
        job3.setMapOutputKeyClass(WordYearFinalResult.class);
        job3.setMapOutputValueClass(DoubleWritable.class);
        job3.setReducerClass(MapReducer3.Reducer3.class);
        job3.setOutputKeyClass(WordYearFinalResult.class);
        job3.setOutputValueClass(DoubleWritable.class);
        job3.setNumReduceTasks(1);
        FileInputFormat.addInputPath(job3, new Path(bucketPath + args[0] + "output_2"));
        FileOutputFormat.setOutputPath(job3, new Path(bucketPath + args[0] + "output_final"));


        ControlledJob jobOneControl = new ControlledJob(job.getConfiguration());
        jobOneControl.setJob(job);

        ControlledJob jobTwoControl = new ControlledJob(job2.getConfiguration());
        jobTwoControl.setJob(job2);

        ControlledJob jobThreeControl = new ControlledJob(job3.getConfiguration());
        jobThreeControl.setJob(job3);

        JobControl jobControl = new JobControl("job-control");
        jobControl.addJob(jobOneControl);
        jobControl.addJob(jobTwoControl);
        jobControl.addJob(jobThreeControl);
        jobTwoControl.addDependingJob(jobOneControl); // this condition makes the job-2 wait until job-1 is done
        jobThreeControl.addDependingJob(jobTwoControl); // this condition makes the job-3 wait until job-2 is done

        Thread jobControlThread = new Thread(jobControl);
        jobControlThread.start();
        int code = 0;
        while (!jobControl.allFinished()) {
            code = jobControl.getFailedJobList().size() == 0 ? 0 : 1;
            Thread.sleep(1000);
        }
        for(ControlledJob j : jobControl.getFailedJobList())
            System.out.println(j.getMessage());
        System.exit(code);
    }
}
