package totem;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

import edu.cmu.sphinx.frontend.util.AudioFileDataSource;
import edu.cmu.sphinx.recognizer.Recognizer;
import edu.cmu.sphinx.result.Result;
import edu.cmu.sphinx.util.props.ConfigurationManager;


public class Transcriber {
	private static final String FILE_MAP_LOCATION = "/Users/heather/Documents/totem/fileMap";
	private ConfigurationManager cm;
	private Recognizer recognizer;
	
	Transcriber(String configFileName){
		//init transcriber
		URL configURL = Transcriber.class.getResource(configFileName);
		cm = new ConfigurationManager(configURL);
        System.out.println("valid config file found: "  + configURL.toString());
        
        recognizer = (Recognizer) cm.lookup("recognizer");
        
        /* allocate the resource necessary for the recognizer */
        recognizer.allocate();
        System.out.println("recognizer allocated");
	}
	
	//TODO refactor into smaller methods
	String transcribeFile(String audioFileName) throws IOException, ClassNotFoundException{
		String resultString = new String();
		File f = new File(audioFileName);
		String chunkDir = "/Users/heather/Documents/totem/chunks/";
		URL audioURL = f.toURI().toURL();

		File transcriptDir = new File("/Users/heather/Documents/totem/transcripts/" + getDate());
		if (!transcriptDir.exists()){
			transcriptDir.mkdir();
		}
		String prefix = transcriptDir + "/" + f.getName().substring(0, f.getName().length()-3);
		File timings = new File(prefix + "timings.txt");
		PrintWriter timingsWriter = new PrintWriter(timings);
		
		System.out.println(audioURL.getPath());
		System.out.println("transcribing to " + timings.getAbsolutePath());
		
        AudioFileDataSource dataSource = (AudioFileDataSource) cm.lookup("audioFileDataSource");
        try {
        	dataSource.setAudioFile(audioURL, null);
        }
        catch( Exception e){
        	System.out.println("couldn't open " + audioURL.getPath());
        	e.printStackTrace();
        	return "";
        }
        System.out.println("audio file found");
		
        // Loop until last utterance in the audio file has been decoded, in which case the recognizer will return null.
        Result result;
       
        while ((result = recognizer.recognize())!= null) {
        	System.out.println("result: " + result.getTimedBestResult(true, false));
        	String lclTimings = result.getTimedBestResult(false, false);
        	if (lclTimings.length() == 0) continue;
        	//System.out.println("original timings: " + lclTimings);
        	//split by whitespace to isolate words
        	
        	String[] wordTimes = lclTimings.split("\\s+");
        	String times = "";
        	String wordsOnly = "";
        	
        	for(String wordTime:  wordTimes){
        		//System.out.println("white space split: " + wordTime);
        		String[] sep = wordTime.split("[(),]");
        		
        		//for(String s: sep) System.out.println("wordTime split: " + s);
        		if (sep.length != 3){
        			System.out.println("separated word/timings != 3, = "+ sep.length);
        			System.out.println("strings: ");
        			for(String s: sep) System.out.println(s);
        		}
        		else{
        			String word = sep[0];
        			double start = Double.valueOf(sep[1]).doubleValue();
        			double end = Double.valueOf(sep[2]).doubleValue();
        			//System.out.println("word: "+ word + " timings: " + start + " - " + end);
        			//check how long the time is 
        			double duration = end - start;
        			if (duration > 1.5){
        				double diff = duration - 1.5;
        				//modify end length to shorten to 1.5 seconds
        				end -= diff;
        				System.out.println("file too long, shortened to 1.5 sec. new endpoint: "+ end);
        			}
        			if (duration < .05){
        				System.out.println("skipping word < .05 seconds");
        				continue; //skip this word
        			}
        			times += word + '(' + start + ',' + end + ") ";
        			wordsOnly += word + ' ';
        			
        			String time = getTime();
        			String date = getDate();
        			String outputAudioFileName =  word + time + ".wav";
        			String dir = chunkDir + date +"/";
        			//if dir does not exist create it
        			File dirFile = new File(dir);
        			if (!dirFile.exists()){
        				dirFile.mkdir();
        			}
        			
					//call SOX to cut the audio
        			int status = soxChunk(audioFileName, word, start, end-start, dir, outputAudioFileName );
        			if (status == 0){
        				//add word: filename mapping to memory
        				try {
        				addToMemory(dir + outputAudioFileName, word);
        				}
        				catch(Exception e){
        					System.out.println("ERROR adding " + dir+ outputAudioFileName +" to memory");
        					e.printStackTrace();
        				}
        			}
        			else System.out.println("sox exit status = " + status + " not saving audio file to map");
        		}
        	}
        	timingsWriter.println(times);
        	resultString+=wordsOnly;
        }
        
        timingsWriter.close();
        
		return resultString;
	}

	private void addToMemory(String audioFileName, String word) throws IOException, ClassNotFoundException {
		//open memory bank
		FileInputStream fis = new FileInputStream(FILE_MAP_LOCATION);
		ObjectInputStream in = new ObjectInputStream(fis);
		@SuppressWarnings("unchecked")
		Map<String,ArrayList<String>> fileMap = (Map<String,ArrayList<String>>) in.readObject();
		fis.close();
		in.close();
		System.out.println("Read filemap");
		//check if word is in memory
		//if it is add new filename to list
		if (fileMap.containsKey(word)){
			ArrayList<String> newFileList = fileMap.get(word);
			newFileList.add(audioFileName);
			fileMap.put(word, newFileList);
		}
		//if it isn't add new word with new filename
		else{
			ArrayList<String> newFileList = new ArrayList<String>();
			newFileList.add(audioFileName);
			fileMap.put(word, newFileList);
		}
		System.out.println("added " + word + " " + audioFileName);
		//write to memory
		FileOutputStream fileMapSerialized = new FileOutputStream(FILE_MAP_LOCATION);
		ObjectOutputStream out = new ObjectOutputStream(fileMapSerialized);
		out.writeObject(fileMap);
		out.close();
		fileMapSerialized.close();
		System.out.println("Wrote new fileMap updated to " + FILE_MAP_LOCATION);
	}

	private int soxChunk(String audioFileName, String word, double start,
			double duration, String outputDir, String outputAudioFileName) throws IOException {
		int x = RunSystemCommand.runSystemCommand("sox " + audioFileName + " " + outputDir + outputAudioFileName + " trim " + start + " " 
				+ duration + " silence 1 1 .1% reverse silence 1 1 .1% fade 0.02 reverse fade 0.02");
		
		return x;
	}
	
	private String getDateTime() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss");
        Date date = new Date();
        return dateFormat.format(date);
    }
	private String getDate() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd");
        Date date = new Date();
        return dateFormat.format(date);
    }
	private String getTime() {
        DateFormat dateFormat = new SimpleDateFormat("HH.mm.ss");
        Date date = new Date();
        return dateFormat.format(date);
    }
}
