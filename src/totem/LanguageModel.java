package totem;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
/*
 * TODO expand to use different number of ngrams
 */

public class LanguageModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public int NGRAM=3;
	Map<Integer, HashMap<ArrayList<String>, Integer>> allNgramCounts; 
	
	private void initializeNgramMap(){
		allNgramCounts = Collections.synchronizedMap(new HashMap<Integer, HashMap<ArrayList<String>, Integer>>());
		for (int i=1; i<=NGRAM; i++){
			HashMap<ArrayList<String>, Integer> counts = new HashMap<ArrayList<String>, Integer>();
			allNgramCounts.put(i, counts);
		}
	}

	public LanguageModel(){
		
	}

	public LanguageModel(int ngram){
		NGRAM = ngram;
		initializeNgramMap();
	}

	public void handle(String s){
		train(s);
	}
	//add tokenized words in the following string to model
	public void train(String s){
		//first tokenize the string by whitespace
		String[] words = s.split("\\s+");

		//iterate over words in the string NGRAM at a time
		//update NGRAM counts
		for (int N=1; N<=NGRAM; N++){
			HashMap<ArrayList<String>, Integer> counts = allNgramCounts.get(N); //get ngram counts for N level
			for (int i=0; i< words.length-N+1; i++){
				String [] subWords = (String[]) ArrayUtils.subarray(words, i, i+N);
				ArrayList<String> ngram = new ArrayList<String>(Arrays.asList(subWords));

				if (counts.containsKey(ngram)){
					Integer x = counts.get(ngram);
					x++;
					counts.put(ngram, x);
				}
				else{
					counts.put(ngram, 1);
				}
			}
		}
	}

	//# unique ngrams in model
	public int getNumNgrams(int ngramLevel){
		return allNgramCounts.get(ngramLevel).size();
	}
	public int getNumAllNgrams() {
		int total = 0;
		for (int i=1; i<=NGRAM; i++){
			total += getNumNgrams(i);
		}
		return total;
	}

	//return hashmap of ngram-> # of occurences for a specific NGRAM level
	public HashMap<ArrayList<String>, Integer> getCounts(int ngramLevel){
		return allNgramCounts.get(ngramLevel);
	}
	
	//return hashmap of ngram-> # of occurences for a all NGRAM levels
	public Map<Integer, HashMap<ArrayList<String>, Integer>> getAllCounts(){
		return allNgramCounts;
	}
	
	public ArrayList<ArrayList<String>> getFlattenedNgramsList() {
		ArrayList<ArrayList<String>> s = new ArrayList<ArrayList<String>>();
		for(int i=1; i<=NGRAM; i++){
			Set<ArrayList<String>> keys = getCounts(i).keySet();
			s.addAll(keys);
		}
		return s;
	}

	public void print(){
		System.out.println("Language Model");
		System.out.println(" using up to " + NGRAM + "-grams");
		for (int i=1; i<=NGRAM; i++){
			System.out.println(i +" -GRAMS");
			HashMap<ArrayList<String>, Integer> counts = allNgramCounts.get(i);
			Set<Entry<ArrayList<String>, Integer>> entries = counts.entrySet();

			Iterator<Entry<ArrayList<String>, Integer>> it = entries.iterator();
			while (it.hasNext()){
				Entry<ArrayList<String>, Integer> entry = it.next();
				System.out.print(entry.getValue() + " - ");
				for (String w: entry.getKey()) System.out.print(w + " ");
				System.out.println();
			}
		}
	}
}
