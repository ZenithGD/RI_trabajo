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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;

/** Simple command-line based search demo. */
public class SearchFiles {

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
        String[] fields = { "title", "subject", "description", "creator", "contributor", "publisher", "date", "type" };
        // BooleanClause.Occur[] flags = { BooleanClause.Occur.SHOULD,
        // BooleanClause.Occur.SHOULD,
        // BooleanClause.Occur.SHOULD, BooleanClause.Occur.SHOULD,
        // BooleanClause.Occur.SHOULD,
        // BooleanClause.Occur.SHOULD };
        String queries = null;
        int repeat = 0;
        boolean raw = false;
        String queryFile = null;
        int hitsPerPage = 10;
        OutputStreamWriter out = null;

        for (int i = 0; i < args.length; i++) {
            if ("-index".equals(args[i])) {
                index = args[i + 1];
                i++;
            }
            /*
             * else if ("-field".equals(args[i])) {
             * fields = args[i+1];
             * i++;
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
            }
        }

        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
        IndexSearcher searcher = new IndexSearcher(reader);
        Analyzer analyzer = new SpanishAnalyzer2();

        BufferedReader in = null;
        if (queryFile != null) {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(queryFile), "UTF-8"));
        } else {
            in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        }
        QueryParser parser = new MultiFieldQueryParser(fields, analyzer);
        int queryIndex = 0;
        while (true) {
            queryIndex++;
            if (queries == null && queryFile == null) { // prompt the user
                System.out.println("Enter query: ");
            }

            String line = in.readLine();

            if (line == null || line.length() == -1) {
                break;
            }

            System.out.println("Consulta : " + line);

            line = line.trim();
            if (line.length() == 0) {
                break;
            }

            Query query = parser.parse(line);
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
                doFullSearch(in, out, searcher, query, queryIndex);
            } else {

                doPagingSearch(in, searcher, query, hitsPerPage, raw,
                        queries == null && queryFile == null);
            }
        }
        if (out != null) {
            out.close();
        }
        reader.close();
    }

    /**
     * Perform a full search based on a query, without pagination. 
     * @param in The input stream
     * @param out The output stream
     * @param searcher Searcher object over the index
     * @param query Phrase that you want to search
     * @param queryIndex number of the query in the document
     * @throws IOException Throws if the file can't be read
     */
    public static void doFullSearch(BufferedReader in, OutputStreamWriter out, IndexSearcher searcher, Query query,
            int queryIndex) throws IOException {

        TotalHitCountCollector collector = new TotalHitCountCollector();
        searcher.search(query, collector);
        TopDocs results = searcher.search(query, Math.max(1, collector.getTotalHits()));
        ScoreDoc[] hits = results.scoreDocs;

        int numTotalHits = Math.toIntExact(results.totalHits.value);
        System.out.println(numTotalHits + " total matching documents");

        for (int i = 0; i < numTotalHits; i++) {
            Document doc = searcher.doc(hits[i].doc);
            String path = doc.get("path");
            if (path != null) {
                out.write(queryIndex + "\t" + path + "\n");
            } else {
                out.write(queryIndex + "\tNo path for this document\n");
            }
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
                String path = doc.get("path");
                if (path != null) {
                    System.out.println((i + 1) + ". " + path);
                    // System.out.println(" modified: " + new
                    // Date(Long.parseLong(doc.get("modified"))));
                    // System.out.println(" identifier: " + doc.get("identifier"));
                } else {
                    System.out.println((i + 1) + ". " + "No path for this document");
                }

                // Explain the scoring function
                // System.out.println(searcher.explain(query, hits[i].doc));

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