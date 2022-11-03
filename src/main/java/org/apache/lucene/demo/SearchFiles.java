package org.apache.lucene.demo;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.*;

import org.apache.lucene.index.Term ;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.stemmer.snowball.SnowballStemmer;
import org.apache.lucene.analysis.es.SpanishLightStemFilter;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.*;
import opennlp.tools.util.Span;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.es.SpanishLightStemmer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.FSDirectory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathNodes;

/** Simple command-line based search demo. */
public class SearchFiles {

    static String[] fields = { "title", "subject", "description", "creator", "contributor", "publisher", "date", "type" };

    private SearchFiles() {
    }

    /** Simple command-line based search demo. */
    public static void main(String[] args) throws Exception {
        String usage = "Usage:\tjava org.apache.lucene.demo.SearchFiles [-index dir] [-field f] [-repeat n] [-queries file] [-query string] [-raw] [-paging hitsPerPage]\n\nSee http://lucene.apache.org/core/4_1_0/demo/ for details.";
        if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
            System.out.println(usage);
            System.exit(0);
        }

        String index = "index";
        BooleanClause.Occur[] flags = {
            BooleanClause.Occur.SHOULD,
            BooleanClause.Occur.SHOULD,
            BooleanClause.Occur.SHOULD,
            BooleanClause.Occur.SHOULD,
            BooleanClause.Occur.SHOULD,
            BooleanClause.Occur.SHOULD,
            BooleanClause.Occur.SHOULD,
            BooleanClause.Occur.SHOULD };
        String queries = null;
        int repeat = 0;
        boolean raw = false;
        String queryFile = null;
        String infoNeedsFile = null;
        String[] identifiers = null;
        int hitsPerPage = 10;
        OutputStreamWriter out = null;
        LinkedHashMap<String,Query> infoNeeds = null;

        
        Analyzer analyzer = new SpanishAnalyzer2();
        
        QueryParser parser = new MultiFieldQueryParser(fields, analyzer);

        for (int i = 0; i < args.length; i++) {
            if ("-index".equals(args[i])) {
                index = args[i + 1];
                i++;
            }
            /*
             * else if ("-field".equals(args[i])) {
             * fields = args[i+1];
             * i++;i
             * }
             */ else if ("-queries".equals(args[i])) {
                queries = args[i + 1];
                i++;
            } else if ("-query".equals(args[i])) {
                queryFile = args[i + 1];

                i++;
            } else if ("-repeat".equals(args[i])) {
                repeat = Integer.parseInt(args[i + 1]);
                i++;
            } else if ("-raw".equals(args[i])) {
                raw = true;
            } else if ("-paging".equals(args[i])) {
                hitsPerPage = Integer.parseInt(args[i + 1]);
                if (hitsPerPage <= 0) {
                    System.err.println("There must be at least 1 hit per page.");
                    System.exit(1);
                }
                i++;
            } else if ("-output".equals(args[i])) {
                out = new OutputStreamWriter(new FileOutputStream(args[i + 1]), "UTF-8");
            } else if ("-infoNeeds".equals(args[i])) {
                infoNeedsFile = args[++i];
                
                infoNeeds = searchInfoNeeds(infoNeedsFile);

                identifiers = infoNeeds.keySet().toArray(new String[0]);
            }

        }

        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
        IndexSearcher searcher = new IndexSearcher(reader);

        BufferedReader in = null;
        if (queryFile != null) {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(queryFile), "UTF-8"));
        } else {
            in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        }
        
        int queryIndex = 0;

        while (true) {

            if (queries == null && queryFile == null) { // prompt the user
                System.out.println("Enter query: ");
            }

            if ( identifiers != null && queryIndex >= identifiers.length ) {
                break;
            }

            String line = infoNeedsFile != null ? identifiers[queryIndex] : in.readLine();

            if (line == null || line.length() == -1) {
                break;
            }

            line = line.trim();
            if (line.length() == 0) {
                break;
            }

            Query query = infoNeeds != null ? infoNeeds.get(identifiers[queryIndex]) : parser.parse(line);
            // System.out.println("Searching for: " + query.toString(fields));

            if (repeat > 0) { // repeat & time as benchmark
                Date start = new Date();
                for (int i = 0; i < repeat; i++) {
                    searcher.search(query, 100);
                }
                Date end = new Date();
                System.out.println("Time: " + (end.getTime() - start.getTime()) + "ms");
            }

            if (out != null) {

                if (infoNeedsFile != null) {
                    doFullSearch(in, out, searcher, query, identifiers[queryIndex]);
                } else {
                    doFullSearch(in, out, searcher, query, line);
                }
            } else {
                doPagingSearch(in, searcher, query, hitsPerPage, raw,
                        queries == null && queryFile == null);
            }

            queryIndex++;
        }
        if (out != null) {
            out.close();
        }
        reader.close();
    }

    public static Query generateQueryFromInfoNeed(String text) throws IOException, org.apache.lucene.queryparser.classic.ParseException {

        NameFinderME nameFinder = null;
        try (InputStream modelIn = new FileInputStream("models/es-ner-location.bin")) {
            TokenNameFinderModel nameFinderModel = new TokenNameFinderModel(modelIn);
            nameFinder = new NameFinderME(nameFinderModel);
        }

        try (InputStream modelIn = new FileInputStream("models/opennlp-es-pos-perceptron-pos-universal.model")) {

            Analyzer analyzer = new SpanishAnalyzer2();
            QueryParser parser = new MultiFieldQueryParser(fields, analyzer);

            //TokenizerModel modelT = new TokenizerModel(modelIn);
            POSModel model = new POSModel(modelIn);

            POSTaggerME tagger = new POSTaggerME(model);
            SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;

            BooleanQuery.Builder bldr = new BooleanQuery.Builder();
            String[] tokens = tokenizer.tokenize(text);
            String[] tags = tagger.tag(tokens);
            Span[] nameSpans = nameFinder.find(tokens);

            for ( int i = 0; i < tags.length; i++ ) {
                tokens[i] = tokens[i].toLowerCase();
            }

            for ( int i = 0; i < tags.length; i++ ) {
                SnowballStemmer stemmer = new SnowballStemmer(SnowballStemmer.ALGORITHM.SPANISH);
                String stem = stemmer.stem(tokens[i]).toString().toLowerCase();
                if (stem.equals("realiz")) {
                    for (Span name : nameSpans) {
                        for (int j = name.getStart(); j < name.getEnd(); j++) {
                            if (j > i) {
                                Query cQuery = parser.parse("creator:" + tokens[j]);
                                BoostQuery boost = new BoostQuery(cQuery, 15f);
                                bldr.add(boost, BooleanClause.Occur.SHOULD);
                            }
                        }
                    }
                }
                else if ( stem.equals("dirig") ) {
                    for ( Span name : nameSpans ) {
                        for ( int j = name.getStart(); j < name.getEnd(); j++ ) {
                            if ( j > i ) {
                                Query cQuery = parser.parse("contributor:" + tokens[j]);
                                BoostQuery boost = new BoostQuery(cQuery, 15f);
                                bldr.add(boost, BooleanClause.Occur.SHOULD);
                            }
                        }
                    }
                } else if ( tags[i].equals("ADP") ) {
                    if ((tokens[i].equals("entre") || tokens[i].equals("de")) && i < tokens.length - 1 && tags[i + 1].equals("NUM")) {
                        String startYear = null, endYear = null;
                        for (; i < tokens.length; i++) {
                            if (tags[i].equals("NUM")) {
                                if (startYear == null) {
                                    startYear = tokens[i];
                                } else if ( endYear == null) {
                                    endYear = tokens[i];
                                    break;
                                }
                            }
                        }

                        TermRangeQuery dQuery = TermRangeQuery.newStringRange("date", startYear, endYear, true, true);
                        bldr.add(dQuery, BooleanClause.Occur.SHOULD);
                    }
                    else if ( tokens[i].equals("en") ) {
                        if ( i < tokens.length - 1 ) {
                            Query cQuery = parser.parse("language:" + tokens[i + 1].substring(0, 2));
                            bldr.add(cQuery, BooleanClause.Occur.SHOULD);
                        }
                        i++;
                    }
                } else if ( stem.equals("tesis") ) {
                    TermQuery tesisQuery = new TermQuery(new Term("type", "TESIS")); 
                    bldr.add(tesisQuery, BooleanClause.Occur.SHOULD);
                } else if ( stem.equals("trabaj") ) {

                    TermQuery tfgQuery = new TermQuery(new Term("type", "TAZ-TFG"));
                    TermQuery tfmQuery = new TermQuery(new Term("type", "TAZ-TFM"));
                    boolean foundKeyword = false;
                    for ( int j = i + 1; j <= i + 4 && j < tokens.length; j++ ) {
                        if (tokens[j].equals("grado")) {
                            bldr.add(tfgQuery, BooleanClause.Occur.SHOULD);
                            i = j;
                            foundKeyword = true;
                            break;
                        } else if (tokens[j].equals("máster")) {
                            bldr.add(tfmQuery, BooleanClause.Occur.SHOULD);
                            i = j;
                            foundKeyword = true;
                            break;
                        }
                    }

                    if ( !foundKeyword ) {
                        bldr.add(tfgQuery, BooleanClause.Occur.SHOULD);
                        bldr.add(tfmQuery, BooleanClause.Occur.SHOULD);
                    }
                } else if (tokens[i].equals("últimos")) {
                    if(tags[i + 1].equals("NUM") && tokens[i + 2].equals("años")){
                        int years = Integer.parseInt(tokens[i+1]);
                        int year = 2022 - years;

                        TermRangeQuery dQuery = TermRangeQuery.newStringRange("date", Integer.toString(year), "2022", true, true);
                        
                        bldr.add(dQuery, BooleanClause.Occur.SHOULD);
                        i +=2;
                    }
                } else if(tokens[i].equals("departamento")){
                    if(tokens[i + 1].equals("de")){
                        String phrase = "";
                        for(int j = i + 2; j < i + 5; j++){
                            if(tags[j].equals("NOUN")){
                                phrase += tokens[j] + " ";
                                i = j;
                            } else {
                                break;
                            }
                        }

                        if(!phrase.isEmpty()){

                            Query pubQuery = parser.parse("publisher:" + phrase );
                            BoostQuery boost = new BoostQuery(pubQuery, 15.0f);
                            bldr.add(boost, BooleanClause.Occur.SHOULD);
                        }
                    }
                } else if (tokens[i].equals("campo") ){

                    for (int j = i + 1; j < tokens.length ; j++){
                        if(tags[j].equals("NOUN")) {
                            Query sQuery = parser.parse("subject:" + tokens[j]);
                            BoostQuery bs = new BoostQuery(sQuery, 15.0f);
                            bldr.add(bs, BooleanClause.Occur.SHOULD);

                            Query tQuery = parser.parse("title:" + tokens[j]);
                            BoostQuery bt = new BoostQuery(tQuery, 10.0f);
                            bldr.add(bt, BooleanClause.Occur.SHOULD);

                            Query dQuery = parser.parse("description:" + tokens[j]);
                            BoostQuery bd = new BoostQuery(dQuery, 10.0f);
                            bldr.add(bd, BooleanClause.Occur.SHOULD);
                            i = j;
                            break;
                        }
                    }
                
                } else if ( tags[i].equals("NOUN") ) {

                    if ( stem.equals("sigl") && i < tokens.length - 1 ) {
                        Query sigQuery = parser.parse("description:" + tokens[++i]);
                        bldr.add(sigQuery, BooleanClause.Occur.SHOULD);
                    } else {
                        Query dQuery = parser.parse("description:" + tokens[i]);
                        bldr.add(dQuery, BooleanClause.Occur.SHOULD);

                        Query sQuery = parser.parse("subject:" + tokens[i]);
                        bldr.add(sQuery, BooleanClause.Occur.SHOULD);

                        Query tQuery = parser.parse("title:" + tokens[i]);
                        bldr.add(tQuery, BooleanClause.Occur.SHOULD);
                    }
                }
            }

            return bldr.build();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static LinkedHashMap<String,Query> searchInfoNeeds(String infoNeedsFile) {

        LinkedHashMap<String,Query> results = new LinkedHashMap<String,Query>();

        try {
            File xmlFile = new File(infoNeedsFile);
            DocumentBuilderFactory factoryInstance = DocumentBuilderFactory.newInstance();
            DocumentBuilder dcb = factoryInstance.newDocumentBuilder();
            org.w3c.dom.Document xmlDoc = dcb.parse(xmlFile);

            // get all info needs
            XPath path = XPathFactory.newInstance().newXPath();
            String idExpr = "/informationNeeds/informationNeed";

            XPathNodes xRes = path.evaluateExpression(idExpr, xmlDoc, XPathNodes.class);

            // loop through info needs
            for ( Node node : xRes ) {
                Element elem = (Element) node;

                String id = elem.getElementsByTagName("identifier").item(0).getTextContent();
                String text = elem.getElementsByTagName("text").item(0).getTextContent();

                Query query = generateQueryFromInfoNeed(text);
                
                // transform the raw info need into a text query which can be parsed by the main program
                results.put(id, query);
            }
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        } catch (org.apache.lucene.queryparser.classic.ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return results;
    }

    /**
     * Perform a full search based on a query, without pagination. 
     * @param in The input stream
     * @param out The output stream
     * @param searcher Searcher object over the index
     * @param query the query to execute
     * @param queryIdentifier identifier of the query in the document
     * @throws IOException Throws if the file can't be read
     */
    public static void doFullSearch(BufferedReader in, OutputStreamWriter out, IndexSearcher searcher, Query query,
            String queryIdentifier) throws IOException {

        TotalHitCountCollector collector = new TotalHitCountCollector();
        searcher.search(query, collector);
        TopDocs results = searcher.search(query, Math.max(1, collector.getTotalHits()));
        ScoreDoc[] hits = results.scoreDocs;

        int numTotalHits = Math.toIntExact(results.totalHits.value);
        System.out.println(numTotalHits + " total matching documents");

        for (int i = 0; i < numTotalHits; i++) {
            Document doc = searcher.doc(hits[i].doc);
            
            String[] pathSplit = doc.get("path").split("\\\\");
            String path = pathSplit[pathSplit.length - 1];
            if (path != null) {
                out.write(queryIdentifier + "\t" + path + "\n");
            } else {
                out.write(queryIdentifier + "\tNo path for this document\n");
            }
            //System.out.println(searcher.explain(query, hits[i].doc));
        }
    }

    /**
     * This demonstrates a typical paging search scenario, where the search engine
     * presents
     * pages of size n to the user. The user can then go to the next page if
     * interested in
     * the next hits.
     * 
     * When the query is executed for the first time, then only enough results are
     * collected
     * to fill 5 result pages. If the user wants to page beyond this limit, then the
     * query
     * is executed another time and all hits are collected.
     * 
     */
    public static void doPagingSearch(BufferedReader in, IndexSearcher searcher, Query query,
            int hitsPerPage, boolean raw, boolean interactive) throws IOException {

        // Collect enough docs to show 5 pages
        TopDocs results = searcher.search(query, 5 * hitsPerPage);
        ScoreDoc[] hits = results.scoreDocs;

        int numTotalHits = Math.toIntExact(results.totalHits.value);
        System.out.println(numTotalHits + " total matching documents");

        int start = 0;
        int end = Math.min(numTotalHits, hitsPerPage);

        while (true) {
            if (end > hits.length) {
                System.out.println("Only results 1 - " + hits.length + " of " + numTotalHits
                        + " total matching documents collected.");
                System.out.println("Collect more (y/n) ?");
                String line = in.readLine();
                if (line.length() == 0 || line.charAt(0) == 'n') {
                    break;
                }

                hits = searcher.search(query, numTotalHits).scoreDocs;
            }

            end = Math.min(hits.length, start + hitsPerPage);

            for (int i = start; i < end; i++) {
                if (raw) { // output raw format
                    System.out.println("doc=" + hits[i].doc + " score=" + hits[i].score);
                    continue;
                }

                Document doc = searcher.doc(hits[i].doc);
                String[] pathSplit = doc.get("path").split("\\\\");
                String path = pathSplit[pathSplit.length - 1];
                if (path != null) {
                    System.out.println((i + 1) + ". " + path);
                    // System.out.println(" modified: " + new
                    // Date(Long.parseLong(doc.get("modified"))));
                    // System.out.println(" identifier: " + doc.get("identifier"));
                } else {
                    System.out.println((i + 1) + ". " + "No path for this document");
                }

                // Explain the scoring function
                //System.out.println(searcher.explain(query, hits[i].doc));

            }

            if (!interactive || end == 0) {
                break;
            }

            if (numTotalHits >= end) {
                boolean quit = false;
                while (true) {
                    System.out.print("Press ");
                    if (start - hitsPerPage >= 0) {
                        System.out.print("(p)revious page, ");
                    }
                    if (start + hitsPerPage < numTotalHits) {
                        System.out.print("(n)ext page, ");
                    }
                    System.out.println("(q)uit or enter number to jump to a page.");

                    String line = in.readLine();
                    if (line.length() == 0 || line.charAt(0) == 'q') {
                        quit = true;
                        break;
                    }
                    if (line.charAt(0) == 'p') {
                        start = Math.max(0, start - hitsPerPage);
                        break;
                    } else if (line.charAt(0) == 'n') {
                        if (start + hitsPerPage < numTotalHits) {
                            start += hitsPerPage;
                        }
                        break;
                    } else {
                        int page = Integer.parseInt(line);
                        if ((page - 1) * hitsPerPage < numTotalHits) {
                            start = (page - 1) * hitsPerPage;
                            break;
                        } else {
                            System.out.println("No such page");
                        }
                    }
                }
                if (quit)
                    break;
                end = Math.min(numTotalHits, start + hitsPerPage);
            }
        }
    }
}