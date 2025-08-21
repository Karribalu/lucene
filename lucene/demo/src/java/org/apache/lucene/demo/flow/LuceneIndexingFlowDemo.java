package org.apache.lucene.demo.flow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KeywordField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Demonstrates the complete Lucene indexing flow with detailed logging
 * to show each step of the process from document creation to storage.
 * 
 * This example provides a step-by-step walkthrough of:
 * 1. Directory and Analyzer setup
 * 2. IndexWriter configuration
 * 3. Document creation and field addition
 * 4. Index writing process
 * 5. Index verification and structure inspection
 */
public class LuceneIndexingFlowDemo {
    
    public static void main(String[] args) throws IOException {
        System.out.println("=== Apache Lucene Indexing Flow Demonstration ===\n");
        
        // Step 1: Setup index directory
        System.out.println("STEP 1: Setting up index directory");
        Path indexPath = Paths.get("demo-index");
        if (Files.exists(indexPath)) {
            // Clean up existing index for fresh demo
            Files.walk(indexPath)
                 .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                 .forEach(path -> {
                     try {
                         Files.deleteIfExists(path);
                     } catch (IOException e) {
                         System.err.println("Could not delete: " + path);
                     }
                 });
        }
        System.out.println("  → Index directory: " + indexPath.toAbsolutePath());
        
        // Step 2: Initialize storage layer
        System.out.println("\nSTEP 2: Initializing storage layer (FSDirectory)");
        Directory directory = FSDirectory.open(indexPath);
        System.out.println("  → Directory implementation: " + directory.getClass().getSimpleName());
        System.out.println("  → Lock factory: " + directory.getLockFactory().getClass().getSimpleName());
        
        // Step 3: Configure text analysis
        System.out.println("\nSTEP 3: Setting up text analysis");
        Analyzer analyzer = new StandardAnalyzer();
        System.out.println("  → Analyzer: " + analyzer.getClass().getSimpleName());
        System.out.println("  → Will tokenize text, remove stop words, apply lowercase filter");
        
        // Step 4: Configure IndexWriter
        System.out.println("\nSTEP 4: Configuring IndexWriter");
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(OpenMode.CREATE);
        config.setRAMBufferSizeMB(64.0); // Set RAM buffer for demo
        System.out.println("  → Open mode: " + config.getOpenMode());
        System.out.println("  → RAM buffer size: " + config.getRAMBufferSizeMB() + " MB");
        System.out.println("  → Codec: " + config.getCodec().getName());
        
        // Step 5: Create IndexWriter and demonstrate indexing
        System.out.println("\nSTEP 5: Creating IndexWriter and indexing documents");
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            System.out.println("  → IndexWriter created successfully");
            System.out.println("  → Writer directory: " + writer.getDirectory());
            
            // Create sample documents to demonstrate different field types
            Document[] sampleDocs = createSampleDocuments();
            
            for (int i = 0; i < sampleDocs.length; i++) {
                System.out.println("\n  --- Indexing Document " + (i + 1) + " ---");
                Document doc = sampleDocs[i];
                
                // Show document structure before indexing
                System.out.println("    Document fields:");
                doc.forEach(field -> {
                    System.out.println("      • " + field.name() + " (" + 
                                     field.getClass().getSimpleName() + "): " + 
                                     field.stringValue());
                });
                
                // Add document to index
                writer.addDocument(doc);
                System.out.println("    → Document added to IndexWriter buffer");
                
                // Show writer stats
                System.out.println("    → Buffered docs: " + writer.getDocStats().numDocs);
                System.out.println("    → RAM usage: " + 
                    String.format("%.2f MB", writer.ramBytesUsed() / 1024.0 / 1024.0));
            }
            
            // Step 6: Demonstrate flushing
            System.out.println("\n  --- Flushing Documents to Disk ---");
            System.out.println("    Before flush - Pending docs: " + writer.getDocStats().numDocs);
            
            writer.flush();
            System.out.println("    → Flush completed");
            System.out.println("    → Documents written to segment files");
            
            // Step 7: Force merge to demonstrate segment management
            System.out.println("\n  --- Optimizing Index (Force Merge) ---");
            writer.forceMerge(1);
            System.out.println("    → Force merge to 1 segment completed");
            
        } // IndexWriter auto-closes here, ensuring all data is written
        
        // Step 8: Verify index and show file structure
        System.out.println("\nSTEP 6: Verifying index and examining file structure");
        try (IndexReader reader = DirectoryReader.open(directory)) {
            System.out.println("  → Index opened successfully for reading");
            System.out.println("  → Total documents: " + reader.numDocs());
            System.out.println("  → Index version: " + DirectoryReader.class.getPackage().getImplementationVersion());
            
            // Show segment information
            if (reader instanceof DirectoryReader) {
                DirectoryReader dirReader = (DirectoryReader) reader;
                System.out.println("  → Number of segments: " + dirReader.leaves().size());
                dirReader.leaves().forEach(leaf -> {
                    System.out.println("    Segment: " + leaf.reader().toString());
                });
            }
        }
        
        // Step 9: Show physical file structure
        System.out.println("\nSTEP 7: Physical file structure on disk");
        try {
            Files.walk(indexPath)
                 .filter(Files::isRegularFile)
                 .sorted()
                 .forEach(file -> {
                     try {
                         long size = Files.size(file);
                         System.out.println("    " + file.getFileName() + 
                                          " (" + size + " bytes) - " + 
                                          describeIndexFile(file.getFileName().toString()));
                     } catch (IOException e) {
                         System.out.println("    " + file.getFileName() + " (size unknown)");
                     }
                 });
        } catch (IOException e) {
            System.err.println("Error listing index files: " + e.getMessage());
        }
        
        // Clean up
        directory.close();
        System.out.println("\n=== Indexing Flow Demonstration Complete ===");
        System.out.println("Index created at: " + indexPath.toAbsolutePath());
    }
    
    /**
     * Creates sample documents with different field types to demonstrate
     * how various data types are processed through the indexing pipeline.
     */
    private static Document[] createSampleDocuments() {
        Document[] docs = new Document[3];
        
        // Document 1: Tech article
        docs[0] = new Document();
        docs[0].add(new KeywordField("id", "doc1", Field.Store.YES));
        docs[0].add(new TextField("title", "Apache Lucene Search Engine", Field.Store.YES));
        docs[0].add(new TextField("content", 
            "Apache Lucene is a high-performance, full-featured text search engine library " +
            "written entirely in Java. It is a technology suitable for nearly any application " +
            "that requires full-text search, especially cross-platform.", Field.Store.YES));
        docs[0].add(new KeywordField("category", "technology", Field.Store.YES));
        docs[0].add(new LongField("timestamp", System.currentTimeMillis(), Field.Store.YES));
        
        // Document 2: Science article
        docs[1] = new Document();
        docs[1].add(new KeywordField("id", "doc2", Field.Store.YES));
        docs[1].add(new TextField("title", "Machine Learning Fundamentals", Field.Store.YES));
        docs[1].add(new TextField("content",
            "Machine learning is a method of data analysis that automates analytical model building. " +
            "It is a branch of artificial intelligence based on the idea that systems can learn from data, " +
            "identify patterns and make decisions with minimal human intervention.", Field.Store.YES));
        docs[1].add(new KeywordField("category", "science", Field.Store.YES));
        docs[1].add(new LongField("timestamp", System.currentTimeMillis() - 3600000, Field.Store.YES));
        
        // Document 3: Programming article
        docs[2] = new Document();
        docs[2].add(new KeywordField("id", "doc3", Field.Store.YES));
        docs[2].add(new TextField("title", "Java Programming Best Practices", Field.Store.YES));
        docs[2].add(new TextField("content",
            "Java is a general-purpose programming language that is class-based, object-oriented, " +
            "and designed to have as few implementation dependencies as possible. Following best practices " +
            "in Java programming can significantly improve code quality and maintainability.", Field.Store.YES));
        docs[2].add(new KeywordField("category", "programming", Field.Store.YES));
        docs[2].add(new LongField("timestamp", System.currentTimeMillis() - 7200000, Field.Store.YES));
        
        return docs;
    }
    
    /**
     * Provides descriptions for different types of index files to help
     * understand what each file contains in the Lucene index structure.
     */
    private static String describeIndexFile(String fileName) {
        if (fileName.startsWith("segments_")) {
            return "Segment metadata file (lists all segments in index)";
        } else if (fileName.endsWith(".si")) {
            return "Segment info file (segment metadata)";
        } else if (fileName.endsWith(".cfs")) {
            return "Compound file (contains multiple index files)";
        } else if (fileName.endsWith(".cfe")) {
            return "Compound file entries (directory for compound file)";
        } else if (fileName.contains("_Lucene") && fileName.endsWith(".fnm")) {
            return "Field names file (field metadata)";
        } else if (fileName.contains("_Lucene") && fileName.endsWith(".fdx")) {
            return "Stored fields index";
        } else if (fileName.contains("_Lucene") && fileName.endsWith(".fdt")) {
            return "Stored fields data";
        } else if (fileName.contains("_Lucene") && fileName.endsWith(".tim")) {
            return "Terms dictionary";
        } else if (fileName.contains("_Lucene") && fileName.endsWith(".tip")) {
            return "Terms index";
        } else if (fileName.contains("_Lucene") && fileName.endsWith(".doc")) {
            return "Postings data (document frequencies)";
        } else if (fileName.contains("_Lucene") && fileName.endsWith(".pos")) {
            return "Positions data (term positions in documents)";
        } else if (fileName.contains("_Lucene") && fileName.endsWith(".pay")) {
            return "Payloads data (additional term data)";
        } else if (fileName.contains("_Lucene") && fileName.endsWith(".nvd")) {
            return "Norms data (field length normalization)";
        } else if (fileName.contains("_Lucene") && fileName.endsWith(".nvm")) {
            return "Norms metadata";
        } else if (fileName.contains("_Lucene") && fileName.endsWith(".dvd")) {
            return "Doc values data";
        } else if (fileName.contains("_Lucene") && fileName.endsWith(".dvm")) {
            return "Doc values metadata";
        } else if (fileName.equals("write.lock")) {
            return "Write lock file (prevents concurrent writes)";
        } else {
            return "Index file";
        }
    }
}