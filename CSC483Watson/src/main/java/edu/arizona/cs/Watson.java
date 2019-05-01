/*
 * Faiz Rafique
 *
 * Final Project, Watson.java, CSC 483
 * The purpose of this program is to implament a smaller version of
 * IBM Watson. This is done with the help of tools such as Lucene and
 * Stanfords Core NLP.
 */

package edu.arizona.cs;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import java.io.*;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.store.FSDirectory;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.lang.Object;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.tartarus.snowball.ext.PorterStemmer;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.*;
import java.util.List;
import java.io.*;
import java.util.Scanner;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.lang.*;
import org.apache.lucene.search.similarities.BooleanSimilarity;

public class Watson
{
    String input_file ="";
    boolean debug;


    /**
     * Constructor class for the program.
     *
     * @param inputFileObj - the name of the input file. In this case the 
     *                       name of the file containing the wikipedia documents 
     *                       ("wiki-subset-20140602").
     */
    public Watson(String inputFileObj, boolean d){
        input_file =inputFileObj;  
        debug      = d;  
    }

   /**
     * Starting point for the program. This defines the name of the input wiki file.
     * Creates the index and determines performance and error analysis.
     *
     * @param args - The command line arguments. In this case determines what index to use
     *               and what scoring function.
     */
    public static void main(String[] args ) {

        try {
            boolean debug = Boolean.parseBoolean(args[0]);
            // name if the file contiaing the wiki documents
            String fileName = "wiki-subset-20140602";

            String q = "Test Query";
           
            // create watson object
            Watson objWatson = new Watson(fileName, debug);
            String[] query13a = objWatson.convertStringToArray(q); 
           // List<ResultClass> ans2 = objWatson.runQ1(query13a);            // create index
            objWatson.loopThroughQuestionsMeasureMRR();                  // measure MRR
            objWatson.loopThroughQuestions();                              // measure # of questions answered correctly   
        }
        // catch exception
        catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    /**
     * Stems a passed in word using Lucenes Porter Stemmer.
     * 
     * @param term - the word being stemmed.
     * @return result - The stemmed word.
     */
    private static String stemTerm(String term) {
        PorterStemmer stem = new PorterStemmer();
        stem.setCurrent(term);
        stem.stem();
        String result = stem.getCurrent();
        return result;
    }

    /**
     * Stems a passed in text using Lucenes Porter Stemmer.
     * 
     * @param text - the text being stemmed.
     * @return modifiedClue - The stemmed text.
     */
    private static String stemText(String text) {
        String modifiedClue = "";
        String[] termArray = text.split(" ");
        for (String word : termArray) {
            modifiedClue = modifiedClue + " " + stemTerm(word);
        }

        modifiedClue = modifiedClue.trim();
        return modifiedClue;
    }

    /**
     * Lemmatizes a passed in text using Stanfords Core NLP.
     * 
     * @param text - the text being stemmed.
     * @return lemmatizedText - The lemmatized text.
     */
    private static String lemmatizeText(String text) {
        String lemmatizedText = "";

         // indexing code. This tokenizes and lemmatizes the documents 
         Properties properties = new Properties();
         properties.setProperty("annotators", "tokenize, ssplit, pos, lemma");
         StanfordCoreNLP stanfordCoreNLP = new StanfordCoreNLP(properties);

         CoreDocument coreDocument = new CoreDocument(text);

         stanfordCoreNLP.annotate(coreDocument);

         List<CoreLabel> coreLabelList = coreDocument.tokens();

         // add each lemma with a space inbetween to get synthetic text.
         for(CoreLabel coreLabel : coreLabelList) {
             String lemma = coreLabel.lemma();
             lemmatizedText = lemmatizedText + " " + lemma;
         } 
         return lemmatizedText;   
    }

    /**
     * Measures the mean reciprocal rank of the system and prints
     * it out to determine its performance.
     */
    private void loopThroughQuestionsMeasureMRR() {
        if (debug) {
            System.out.println("------------------------------STARTING MRR CODE---------------------------------------------------------------");
            System.out.println();
            
        }
        // loop through questions file
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("questions.txt").getFile());
        float rank = 1;
        boolean found = false;
        
        try  {
            // set variables
            Scanner scanner = new Scanner(file);
            String fileName = "wiki-subset-20140602";
            Watson objWatson = new Watson(fileName, debug);
            int correctCount = 0;
            float[] averages = new float[100];
            int i = 0;


            while (scanner.hasNextLine()) {
                // get the parts from the questions file
                String category = scanner.nextLine();
                String clue     = scanner.nextLine();
                String answer   = scanner.nextLine();
                String blank    = scanner.nextLine();

                if (debug) {
                    System.out.println();
                    System.out.println();
                    System.out.println(category);
                    System.out.println(clue);
                    System.out.println(answer);
                    System.out.println(blank);

                }
                
                // add the category to the clue
                clue = category + " " + clue;
        
                // lowercase text
                clue = clue.toLowerCase();
                
                // stem text
                clue = stemText(clue);

                // lemmatize text 
                //clue = lemmatizeText(clue);

                // escape special characters
                clue = escapeSpecialCharacters(clue);
                
                // pass the query/clue into the index
             
                List<ResultClass>  ans=new ArrayList<ResultClass>();
                ans = objWatson.runQ1(objWatson.convertStringToArray(clue.trim()));

                for (ResultClass docs : ans) {
                    if (docs.DocName.get("docid").substring(2, docs.DocName.get("docid").length() - 2).equals(answer)) {
                        averages[i] = 1/rank;

                        if (debug) {
                            System.out.println();
                            System.out.println("Reciprocal Rank for this document: " + 1/rank);
                            System.out.println();
                        }
                        
                        i +=1;
                        rank = 1;
                        found = true;
                        break;
                    }  
                    rank +=1;
                }
                
                // set average to 0 if doc isnt in the first 300 hits.
                if (rank >= 300) {
                    averages[i] = 0;
                    i += 1;
                    rank = 1;
                }

                found = false;

                if (debug) {
                    System.out.print("Averages so far: ");
                    System.out.println(java.util.Arrays.toString(averages));
                    System.out.println();

                }
                
            }

            System.out.print("MRR averages accross all documents: ");
            System.out.println(java.util.Arrays.toString(averages));
            System.out.println();

            float avgCount = 0;

            for (float val : averages) {
                avgCount += val;
            }

            // print out the MRR
            System.out.println("MRR: " + avgCount/100);
            scanner.close();
            if (debug) {
                System.out.println("------------------------------ENDING MRR CODE---------------------------------------------------------------");
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("oof");
        } 
    }

    /**
     * Escapes Lucene special characters in the query.
     * 
     * @param clue - the query being modified.
     * @return modifiedClue - the clue with special characters escaped.
     */
    private static String escapeSpecialCharacters(String clue) {
        String modifiedClue = "";
        String[] specialCharacters = {"+",  "-", "&&", "||", "!", "(", ")", "{", "}", "[", "]",  "^", "\"", "~", "*", "?", ":", "\\"};
        
        for (int i = 0; i < clue.length(); i++) {
            for (int j = 0; j < specialCharacters.length; j++) {
                if (specialCharacters[j].equals(clue.substring(i, i+1))) {
                    modifiedClue = modifiedClue + "\\";
                }
            }
            modifiedClue = modifiedClue + clue.substring(i, i+1);   
        }
        return modifiedClue;
    }

    /**
     * Measures how many questions were answered correctly.
     */
    private void loopThroughQuestions() {
        if (debug) {
            System.out.println();
            System.out.println();
            System.out.println("------------------------------STARTING P@1 CODE---------------------------------------------------------------");
            System.out.println();
            
        }
        // get the questions file.
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("questions.txt").getFile());

        try  {
            // set variables
            Scanner scanner = new Scanner(file);
            String fileName = "wiki-subset-20140602";
            Watson objWatson = new Watson(fileName, debug);
            int correctCount = 0;

            while (scanner.hasNextLine()) {
                // get the seperate pieces of the questions file.
                String category = scanner.nextLine();
                String clue     = scanner.nextLine();
                String answer   = scanner.nextLine();
                String blank    = scanner.nextLine();

                String modifiedClue = "";

                if (debug) {
                    System.out.println();
                    System.out.println();
                    System.out.println(category);
                    System.out.println(clue);
                    System.out.println(answer);
                    System.out.println(blank);

                }
  
                // modify clue
                List<ResultClass>  ans=new ArrayList<ResultClass>();
              
                // add the category to the clue
                clue = category + " " + clue;
        
                // lowercase text
                clue = clue.toLowerCase();
                
                // stem text
                clue = stemText(clue);

                // lemmatize text 
                //clue = lemmatizeText(clue);

                // escape special characters
                clue = escapeSpecialCharacters(clue);
                
                // pass the query/clue into the index
                ans = objWatson.runQ1(objWatson.convertStringToArray(clue.trim()));

                for (ResultClass docs : ans) {
                   
                    if (docs.DocName.get("docid").substring(2, docs.DocName.get("docid").length() - 2).equals(answer)) {
                        correctCount +=1;
                    }
                    break;
                }   
            }

            // print out how many were right
            System.out.println("Precision at 1: " + correctCount);

            scanner.close();

            if (debug) {
                System.out.println();
                System.out.println();
                System.out.println("------------------------------ENDING P@1 CODE---------------------------------------------------------------");
                System.out.println();
                
            }

        } catch (IOException e) {
            e.printStackTrace();
            
        }  
    }

    /**
     * Calls the indexer and query parsing code while checking for exceptions.
     * 
     * @param query - the query being found.
     * @return ans - a list of valid documents.
     */
    public List<ResultClass> runQ1(String[] query) throws java.io.FileNotFoundException,java.io.IOException {
        
        // run the query
        List<ResultClass>  ans=new ArrayList<ResultClass>();
        try {
            
            ans = findMatchingDocs(query);
        }

        // exception catching
        catch(FileNotFoundException e) {
            System.out.println(e.getMessage());   
        }
        catch(ParseException e ) {
            System.out.println(e.getMessage());
        }

        return ans;
    }

    /**
     * Adds a doc to the index.
     * 
     * @param w - the indexwriter. 
     * @param title - the title of the document. 
     * @param docid - the docid of the document.
     * @param query - the query being found.
     *
     */
    private static void addDoc(IndexWriter w, String content, String docid) throws IOException {
        Document doc = new Document();
        doc.add(new TextField("title", content, Field.Store.YES));

        // use a string field for docid because we don't want it tokenized
        doc.add(new StringField("docid", docid, Field.Store.YES));
        w.addDocument(doc);
    }

    /**
     *  Converts an array to a single string
     * 
     * @param arr - the array being converted.
     * @return the array in string form.
     *
     */
    private String convertArrayToString(String[] arr) {
        String val = "";
        for (int i = 0; i < arr.length; i++) {
            val = val + " " + arr[i]; 
        }

        return val.trim();
    }

    /**
     *  Converts an string to an array.
     * 
     * @param arr - the string being converted.
     * @return the string in string array.
     *
     */
    private String[] convertStringToArray(String arr) {
        String[] val = arr.split(" ");
        return val;    
    }

    /**
     *  Filters out extra wikipedia metadata that could be mistaken for a document
     * title.
     * 
     * @param arr - the title thats being validated.
     * @return a boolean indicating if the passed in string is a title or not.
     *
     */
    private boolean checkForValidTitle(String arr) {
        String temp = arr.toLowerCase();
        String[] temp2 = temp.split(":");
        
        if (temp2[0].equals("[[image") || temp2[0].equals("[[file") || temp2[0].equals("[[media")) {
            return false;
        }

        String[] temp3 = temp.split(",");
        return true;  
    }

     /**
     *  Creates the index and finds matching docs to the query
     * 
     * @param query - the query 
     * @return a list of the top n hits for the query.
     *
     */
    private List<ResultClass> findMatchingDocs(String[] query) throws IOException, ParseException {
        // this is just to make sure that the Lucene dependency works
        StandardAnalyzer analyzer = new StandardAnalyzer();

        // 1. create the index
        FSDirectory index = FSDirectory.open(Paths.get("src/main/resources/luceneindexStem/"));
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        IndexWriter w = new IndexWriter(index, config);
        StringBuilder result = new StringBuilder("");
        boolean buildIndex = false;
        
        if (buildIndex) {
            
            //Get file from resources folder
            ClassLoader classLoader = getClass().getClassLoader();
            File directory = new File(classLoader.getResource(input_file).getFile());

            // loop through wiki docs
            File[] listOfFiles = directory.listFiles();
            boolean firstFile;
            for (File file : listOfFiles) {
                    // special case for the fisrt file
                    firstFile = true;
                    String temp = "";
                    String docId = "";
                    
                    // scan files and get text
                    try  {
                    Scanner scanner = new Scanner(file);
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        
                        // if we are at the title
                        if (line.length() > 2 && line.charAt(0) == '[' && line.charAt(1) == '[' && line.charAt(line.length()-1) == ']' && line.charAt(line.length()-2) == ']') {
                            if (checkForValidTitle(line)) {
                                if (firstFile) {
                                    docId = line;
                                    firstFile = false;
                                }
                                
                                // if we are the end of a docs text
                                if (!temp.equals("")) {
                                    temp = temp.toLowerCase();

                                    // Stemming code
                                    temp = stemText(temp);
                                    
                                    // Lemmatize text
                                    temp = lemmatizeText(temp);

                                    // add doc and text to the index
                                    addDoc(w, temp.trim(), docId);
                                }
                                docId = line;       
                                    temp = "";
                            }    
                        }
                        
                        // else add the text of the doc up
                        else {
                            temp = temp + line;
                            temp = temp + '\n';
                        }    
                    }

                    scanner.close();

                // catch exception
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("oof");
                }
               
                temp = temp.toLowerCase();
                
                // Stemming code
                temp = stemText(temp);
                // Lemmatize text
                temp = lemmatizeText(temp);

                // add last doc in the file to the index
                addDoc(w, temp.trim(), docId);
            }
        }
 
        w.close();

        String convertedQ = convertArrayToString(query);

        // the "title" arg specifies the default field to use
        // when no field is explicitly specified in the query.
        Query q = new QueryParser("title", analyzer).parse(convertedQ);
 
        // 3. search
        int hitsPerPage = 100;
        IndexReader reader = DirectoryReader.open(index);
        IndexSearcher searcher = new IndexSearcher(reader);

        // $$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$ THIS LINE CHANGES THE SCORING FUNCTION TO BOOLEANSIMILARITY

        //searcher.setSimilarity(new BooleanSimilarity());

        // $$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$ THIS LINE CHANGES THE SCORING FUNCTION TO BOOLEANSIMILARITY


        // get top docs for the query
        TopDocs docs = searcher.search(q, hitsPerPage);
        ScoreDoc[] hits = docs.scoreDocs;
   
        List<ResultClass> doc_score_list = new ArrayList<ResultClass>();
 
        // 4. display results
        for(int i=0;i<hits.length;++i) {
            int docId = hits[i].doc;
            Document d = searcher.doc(docId);
            ResultClass objResultClass= new ResultClass();
            objResultClass.DocName=d;
            doc_score_list.add(objResultClass);
            if (debug) {
                System.out.println("Hit:" + (i + 1) + " " +  "DocName: " + d.get("docid") + " " + "DocScore: " + hits[i].score);
            }
            
        }

        // reader can only be closed when there
        // is no need to access the documents any more.
        reader.close();

        return doc_score_list;

    }   
}
