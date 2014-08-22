package totem;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.aliasi.stats.Statistics;
import com.aliasi.util.ScoredObject;

public class TotemMain {

	/*
	 * This class runs the recording of new audio and speaking
	 */

	private static final String FILE_MAP_LOCATION = "/Users/heather/Documents/totem/fileMap";
	private static final String LANGUAGE_MODEL_LOCATION = "/Users/heather/Documents/totem/totem.lm";
	private static final long BITRATE = 705600;

	public static void main(String[] args){
		String dest = "/Users/heather/Documents/totem/audioToProcess";
		File dir = new File("/Users/heather/Documents/totem/recordings");
		FileFilter filter = new NonHiddenFileFilter();

		if (!dir.canRead()) {
			System.out.println("invalid recording directory");
			System.exit(0);
		}
		File[] children;


		/*
		 * MAIN LOOP
		 */
		while (true) {
			// run record
			// start voice activated recorder
			RunSystemCommand
			.runSystemCommand("/Applications/Recordpad.app/Contents/MacOS/RecordPad -record -exitifstop");
			
			try {
				speechConduct();
			} catch (Exception e) {
				System.out.println("couldn't speak ");
				e.printStackTrace();
			}
			// scan output directory for a new FINISHED file
			children = dir.listFiles(filter);
			System.out.println("dir: " + children.toString());
			System.out.println("# files: " + children.length);

			for (int i = 0; i < children.length; i++) {
				// Get filename of file or directory
				System.out.println(children[i].getName());

				// move the file for processing
				children[i].renameTo(new File(dest, children[i].getName()));
				System.out.println("moved to " + dest);
			}
		}
	}

	private static void speechConduct() throws IOException,
	ClassNotFoundException, InterruptedException {
		File fileMapFile = new File(FILE_MAP_LOCATION);
		if (!fileMapFile.exists()){
			System.out.println("no filemap yet");
			return;
		}
		// open fileMap
		Map<String, ArrayList<String>> fileMap = openFileMap();
		
		File lmFile = new File(LANGUAGE_MODEL_LOCATION);
		if (!lmFile.exists()){
			System.out.println("no language model yet");
			return;
		}

		LanguageModel lm = openLanguageModel();

		ArrayList<String> words = getWords(lm);

		System.out.println("words chosen: " + words);

		speak(words, fileMap); // method to lookup files and concatenate
		// together and call play
		System.out.println("spoke " + words);
	}

	private static ArrayList<String> getWords(LanguageModel lm) {
		// get starting ngram
		ArrayList<String> words = getFirstWords(lm);
		System.out.println("random choice first ngram: " + words);
		ArrayList<String> wordsNew = getRestWords(words, lm);
		// get more words if more words are available
		// but only up to 10 times
		int max = 10;
		int count = 1;

		while ((wordsNew.size() > words.size()) && (count < max)) {
			words = wordsNew;
			wordsNew = getRestWords(words, lm);
			count++;
		}
		return wordsNew;
	}

	private static ArrayList<String> getRestWords(ArrayList<String> words, LanguageModel lm) {
		ArrayList<String> retWords = new ArrayList<String>();
		retWords.addAll(words);
		Map<Integer, HashMap<ArrayList<String>, Integer>> counts = lm.getAllCounts();		
		ArrayList<ScoredObject<ArrayList<String>>> masterList = new ArrayList<ScoredObject<ArrayList<String>>>();

		// skip unigrams
		for (int i = 2; i <= lm.NGRAM; i++) {
			System.out.println("checking " + i + "-grams");

			if (words.size() < (i - 1))
				break; // ie. words size = 1, ngram value = 3 exhausted our options

			HashMap<ArrayList<String>, Integer> cn = counts.get(i);
			Set<ArrayList<String>> possibles = cn.keySet();
			// see if we have an entry for the last i-1 words in words
			int end = words.size();
			int begin = end - (i - 1);
			List<String> currentNgram = words.subList(begin, end);
			//System.out.println("current ngram: " + currentNgram);
			for (ArrayList<String> s : possibles) {
				int match = 0;
				for (int j = 0; j < currentNgram.size(); j++) {
					if (s.get(j) == currentNgram.get(j)) {
						match++;
					}
				}
				if (match == i - 1) {
					//System.out.println("match: " + s);
					ScoredObject<ArrayList<String>> so = new ScoredObject<ArrayList<String>>(s, cn.get(s));
					masterList.add(so);
				}
			}
		}
		// choose a match
		if (masterList.size() > 0) {
			ArrayList<String> winner = chooseSample(masterList);

			retWords.addAll(winner.subList(winner.size() - 1, winner.size()));
		}
		return retWords;
	}

	private static ArrayList<String> chooseSample(
			ArrayList<ScoredObject<ArrayList<String>>> masterList) {
		ArrayList<String> retStrings;
		double[] cumProbs = new double[masterList.size()];

		//iterate over set and add score to sum
		double sum = 0.0;
		int i=0;
		for(ScoredObject<ArrayList<String>> so: masterList){
			sum += so.score();
			cumProbs[i] = sum;
			i++;
		}
		Random ran = new Random();
		int ind = Statistics.sample(cumProbs, ran);

		//iterate to choice
		ScoredObject<ArrayList<String>> choice = masterList.get(ind);
		retStrings=choice.getObject();
		return retStrings;
	}


	// not used; for testing only
	@SuppressWarnings("unused")
	private static ArrayList<String> chooseRandomWords(
			Map<String, ArrayList<String>> fileMap) {
		Set<String> words = fileMap.keySet();
		Random ran = new Random();
		ArrayList<String> retWords = new ArrayList<String>();
		for (int i = 0; i < 5; i++) {
			int ind = ran.nextInt(words.size());
			retWords.add((String) words.toArray()[ind]);
		}
		return retWords;
	}

	static String chooseFile(Collection<ArrayList<String>> possibles) {
		// get a random index
		Random ran = new Random();
		int ind = ran.nextInt(possibles.size());
		Iterator<ArrayList<String>> it = possibles.iterator();
		ArrayList<String> randomList = it.next();
		for (int i = 1; i < ind; i++) {
			randomList = it.next();
		}
		// randomize this list
		Collections.shuffle(randomList);
		String s = randomList.get(0);
		// grab a file from the list
		return s;
	}

	private static void speak(ArrayList<String> words,
			Map<String, ArrayList<String>> fileMap) throws IOException {
		// for each word choose a file name
		Random ran = new Random();
		ArrayList<String> audioFiles = new ArrayList<String>();

		for (String w : words) {
			if (fileMap.containsKey(w)) {
				ArrayList<String> choices = fileMap.get(w);
				// random choice from files
				audioFiles.add(choices.get(ran.nextInt(choices.size())));
			}
		}
		System.out.println(audioFiles);
		// call sox to concatenate each word onto a growing file
		if (audioFiles.size() > 1) {
			// concat 1st 2 files
			RunSystemCommand
			.runSystemCommand("/Users/heather/Documents/totem/crossfade_cat.sh .05 "
					+ audioFiles.get(0)
					+ " "
					+ audioFiles.get(1)
					+ " auto auto");
			audioFiles.remove(0);
			audioFiles.remove(0);
			for (String s : audioFiles) {
				RunSystemCommand.runSystemCommand("/Users/heather/Documents/totem/crossfade_cat.sh .05 mix.wav " + s + " auto auto");
			}
			// call sox to play the final file
			// RunSystemCommand.runSystemCommand("play mix.wav");
			// make quicktime run the file
			callQuicktime("mix.wav");
		} else if (audioFiles.size() == 1) {
			System.out.println("only 1 file");
			callQuicktime(audioFiles.get(0));
		} else {
			System.out.println("no files to play");
		}

	}

	private static void callQuicktime(String s) {
		File f = new File("mix.wav");
		double delay = ((double) f.length() / (double) BITRATE) * 1000;
		System.out.println(f.length());
		String[] arg = { "open", "/Applications/QuickTime Player.app", s };

		try {
			Runtime.getRuntime().exec(arg);
			System.out.println("delay " + delay);
			Thread.sleep(3500 + (long) delay); // takes about 3 sec. to open app
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();  
		}
		System.out.println("done delay");
		RunSystemCommand.runSystemCommand("quit QuickTime Player.app");
	}

	private static ArrayList<String> getFirstWords(LanguageModel lm) {
		ArrayList<String> words = new ArrayList<String>();

		int numNgrams = lm.getNumAllNgrams();
		System.out.println("Ngrams stored " + numNgrams);
		if (numNgrams == 0){
			System.out.println("no ngrams to choose from");
			return words; // return empty

		}
			
		ArrayList<ArrayList<String>> ngrams = lm.getFlattenedNgramsList();
		System.out.println("size of flattened ngrams: " + ngrams.size());
		Random ran = new Random();
		int ind = ran.nextInt(ngrams.size());
		//System.out.println(ngrams);
		System.out.println("random ind: " + ind);
		System.out.println("word: " + ngrams.get(ind));
		return ngrams.get(ind);
	}
	
	private static Map<String, ArrayList<String>> openFileMap()
	throws FileNotFoundException, IOException, ClassNotFoundException {
		FileInputStream fis = new FileInputStream(FILE_MAP_LOCATION);
		ObjectInputStream in = new ObjectInputStream(fis);
		@SuppressWarnings("unchecked")
		Map<String, ArrayList<String>> fileMap = (Map<String, ArrayList<String>>) in
		.readObject();
		fis.close();
		in.close();
		return fileMap;
	}
	private static LanguageModel openLanguageModel() throws IOException,
	ClassNotFoundException {
		FileInputStream fis = new FileInputStream(LANGUAGE_MODEL_LOCATION);
		ObjectInputStream in = new ObjectInputStream(fis);
		LanguageModel lm = (LanguageModel) in.readObject();
		fis.close();
		in.close();
		return lm;
	}
}
