package totem;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ProcessRecording {
	private static final String FILE_MAP_LOCATION = "/Users/heather/Documents/totem/fileMap";
	private static final String LANGUAGE_MODEL_LOCATION = "/Users/heather/Documents/totem/totem.lm";
	private static final int NGRAM = 3;

	/**
	 * This class runs the processing of new recordings, chunking and adding to memory
	 * @throws ClassNotFoundException 
	 */
	public static void main(String[] args)  {
		String dest = "/Users/heather/Documents/totem/processed/";
		File dir = new File("/Users/heather/Documents/totem/audioToProcess");
		FileFilter filter = new NonHiddenFileFilter();

		if (!dir.canRead()) {
			System.out.println("invalid audio processing directory");
			System.exit(0);
		}

		File[] children;
		System.out.println("initializing transcriber");
		Transcriber transcriber = new Transcriber("hub4.config.xml");
		//Transcriber transcriber = new Transcriber("hellongram.config.xml");

		// check if our memory map exists - if not create a new empty one.
		File fileMapFile = new File(FILE_MAP_LOCATION);
		if (!fileMapFile.exists()) {
			try {
				createFileMap();
			} catch (FileNotFoundException e) {
				System.out.println("couldn't create file map");
				e.printStackTrace();
				System.exit(-1);
			} catch (IOException e) {
				System.out.println("couldn't create file map");
				e.printStackTrace();
				System.exit(-1);
			}
		} else
			System.out.println("fileMap exists");

		/*
		 * MAIN LOOP
		 */
		while(true){
			System.out.println("waiting for new files to process");
			children = dir.listFiles(filter);
			//scan audioToProcess directory for new files
			while (children.length == 0){
				children = dir.listFiles(filter);
			}

			System.out.println("files found - # files: " + children.length);

			//process audio files 	
			for (int i=0; i<children.length; i++) {
				//System.out.println(children[i].getName());
				//process new audio files
				//transcribe - write transcription to file AND return it
				//this also chunks the file into pieces and adds word: filename mapping  to memory
				String words;
				try {
					words = transcriber.transcribeFile(children[i].getCanonicalPath());
				} catch (Exception e) {
					System.out.println("couldn't transcribe file");
					e.printStackTrace();
					continue;
				} 
				System.out.println("words: " + words);
				
				if (words.length()>0){
					//add to LM
					// check if our language model exists - if not create one
					File languageModelFile = new File(LANGUAGE_MODEL_LOCATION);
					
					if (!languageModelFile.exists()) {
						try {
							createLanguageModel(languageModelFile, words);
						} catch (FileNotFoundException e) {
							System.out.println("couldn't create language model");
							e.printStackTrace();
							System.exit(-1);
						} catch (IOException e) {
							System.out.println("couldn't create language model");
							e.printStackTrace();
							System.exit(-1);
						}
					} 
					else{
						LanguageModel lm;
						try {
							
							lm = openLanguageModel();
						} catch (Exception e) {
							System.out.println("couldn't open language model");
							e.printStackTrace();
							continue;
						} 
	
						lm.train(words);
						try {
							writeLanguageModel(lm);
						} catch (IOException e) {
							System.out.println("couldn't write language model");
							e.printStackTrace();
						}
						System.out.println("added "+ words + " to LM and reserialized");
					}
				
				}
				//move the file finished with processing
				String newDest = dest + getDate();
				//if dir does not exist create it
				File dirFile = new File(newDest);
				if (!dirFile.exists()){
					dirFile.mkdir();
				}

				children[i].renameTo(new File(newDest, children[i].getName()));
				System.out.println("moved recording to " + dest);
			}
		}
	}

	private static String getDate() {
		DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd");
		Date date = new Date();
		return dateFormat.format(date);
	}
	private static void createFileMap() throws FileNotFoundException,
	IOException {
		Map<String, ArrayList<String>> fileMap = Collections
		.synchronizedMap(new HashMap<String, ArrayList<String>>());

		FileOutputStream fileMapSerialized = new FileOutputStream(
				FILE_MAP_LOCATION);
		ObjectOutputStream out = new ObjectOutputStream(fileMapSerialized);
		out.writeObject(fileMap);
		out.close();
		fileMapSerialized.close();

		System.out.println("Wrote new fileMap to: " + FILE_MAP_LOCATION);
	}

	private static void createLanguageModel(File languageModelFile, String text)
	throws FileNotFoundException, IOException {
		System.out.println("creating new language model at "
				+ LANGUAGE_MODEL_LOCATION);
		
		LanguageModel lm = new LanguageModel(NGRAM);
		lm.train(text);
		lm.print();

		FileOutputStream lmSerialized = new FileOutputStream(languageModelFile);
		ObjectOutputStream out = new ObjectOutputStream(lmSerialized);
		out.writeObject(lm);
		lmSerialized.close();
		out.close();
	}

	private static LanguageModel openLanguageModel() throws IOException, ClassNotFoundException {
		FileInputStream fis = new FileInputStream(LANGUAGE_MODEL_LOCATION);
		ObjectInputStream in = new ObjectInputStream(fis);
		LanguageModel lm = (LanguageModel) in.readObject();
		//lm.print();
		fis.close();
		in.close();
		return lm;
	}
	private static void writeLanguageModel(LanguageModel lm) throws IOException {
		//lm.print();
		FileOutputStream lmSerialized = new FileOutputStream(LANGUAGE_MODEL_LOCATION);
		ObjectOutputStream out = new ObjectOutputStream(lmSerialized);
		out.writeObject(lm);
		lmSerialized.close();
		out.close();
	}
}
