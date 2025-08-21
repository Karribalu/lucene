# Lucene Indexing Data Flow Diagrams

This document provides visual diagrams showing the data flow through the Lucene indexing pipeline.

## 1. High-Level Architecture Flow

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              APPLICATION LAYER                                      │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  IndexFiles.main()                                                                  │
│  ├── Parse command line arguments                                                   │
│  ├── Initialize Directory (FSDirectory)                                             │
│  ├── Create Analyzer (StandardAnalyzer)                                             │
│  ├── Configure IndexWriterConfig                                                    │
│  └── Create IndexWriter                                                             │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              DOCUMENT LAYER                                         │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  For each file to index:                                                            │
│  ├── Create Document object                                                         │
│  ├── Add KeywordField (path)                                                        │
│  ├── Add LongField (modified date)                                                  │
│  ├── Add TextField (contents)                                                       │
│  ├── Add KnnFloatVectorField (optional)                                             │
│  └── Call writer.addDocument(doc)                                                   │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              INDEX WRITER LAYER                                     │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  IndexWriter                                                                         │
│  ├── Manages DocumentsWriter                                                        │
│  ├── Controls MergePolicy                                                           │
│  ├── Handles FlushPolicy                                                            │
│  ├── Coordinates Analyzer usage                                                     │
│  └── Manages thread synchronization                                                 │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              PROCESSING LAYER                                       │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  DocumentsWriter → DocumentsWriterPerThread → IndexingChain                         │
│                                                                                     │
│  IndexingChain.processDocument():                                                   │
│  ├── termsHash.startDocument()                                                      │
│  ├── startStoredFields(docID)                                                       │
│  ├── For each field: processField(docID, field, perField)                          │
│  └── finishDocument()                                                               │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              FIELD PROCESSING LAYER                                 │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐    │
│  │ StoredFields    │ │ TermVectors     │ │ Postings        │ │ DocValues       │    │
│  │ Consumer        │ │ Consumer        │ │ (TermsHash)     │ │ Consumer        │    │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘ └─────────────────┘    │
│                                                                                     │
│  ┌─────────────────┐ ┌─────────────────┐                                           │
│  │ Points          │ │ Vectors         │                                           │
│  │ Writer          │ │ Consumer        │                                           │
│  └─────────────────┘ └─────────────────┘                                           │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              CODEC LAYER                                            │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  Codec (Lucene99Codec)                                                              │
│  ├── PostingsFormat     → Inverted index encoding                                   │
│  ├── StoredFieldsFormat → Stored fields encoding                                    │
│  ├── TermVectorsFormat  → Term vectors encoding                                     │
│  ├── DocValuesFormat    → Doc values encoding                                       │
│  ├── PointsFormat       → Numeric points encoding                                   │
│  ├── KnnVectorsFormat   → Vector data encoding                                      │
│  ├── NormsFormat        → Field norms encoding                                      │
│  └── FieldInfosFormat   → Field metadata encoding                                   │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              STORAGE LAYER                                          │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  FSDirectory                                                                        │
│  ├── createOutput() → FSIndexOutput                                                 │
│  ├── openInput() → FSIndexInput                                                     │
│  ├── Manages pending deletes                                                        │
│  └── Handles file operations                                                        │
│                                                                                     │
│  Storage Implementations:                                                           │
│  ├── NIOFSDirectory  (Java NIO FileChannel)                                        │
│  ├── MMapDirectory   (Memory-mapped files)                                          │
│  └── ByteBuffersDirectory (In-memory)                                               │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              PHYSICAL STORAGE                                       │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  File System Structure:                                                             │
│  index/                                                                             │
│  ├── segments_1                  (Segment metadata)                                 │
│  ├── _0.cfs, _0.cfe             (Compound files)                                    │
│  ├── _0.si                      (Segment info)                                      │
│  ├── _0_Lucene99_0.doc/.fdx     (Stored fields)                                     │
│  ├── _0_Lucene99_0.tim/.tip     (Terms dictionary/index)                            │
│  ├── _0_Lucene99_0.doc/.pos/.pay (Postings: docs/positions/payloads)               │
│  ├── _0_Lucene99_0.nvd/.nvm     (Norms data/metadata)                               │
│  ├── _0_Lucene99_0.dvd/.dvm     (Doc values data/metadata)                          │
│  └── write.lock                 (Write lock)                                        │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## 2. Detailed Processing Pipeline Flow

```
Document Input
     │
     ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                         IndexingChain.processDocument()                             │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  Phase 1: Document Setup                                                            │
│  ├── termsHash.startDocument()                                                      │
│  ├── startStoredFields(docID)                                                       │
│  └── Initialize field processing arrays                                             │
│                                                                                     │
│  Phase 2: Field Schema Validation                                                   │
│  ├── For each field in document:                                                    │
│  │   ├── Get or create PerField instance                                            │
│  │   ├── Validate field type consistency                                            │
│  │   └── Build field schema                                                         │
│  └── Verify no reserved field conflicts                                             │
│                                                                                     │
│  Phase 3: Field Processing                                                          │
│  ├── For each field in document:                                                    │
│  │   ├── processField(docID, field, perField)                                       │
│  │   │   ├── Handle stored values                                                   │
│  │   │   ├── Process inverted index (if indexed)                                    │
│  │   │   ├── Handle doc values                                                      │
│  │   │   ├── Process points (numeric data)                                          │
│  │   │   └── Handle vectors (KNN data)                                              │
│  │   └── Update field statistics                                                    │
│  └── Track processed fields                                                         │
│                                                                                     │
│  Phase 4: Document Finalization                                                     │
│  ├── For each processed field:                                                      │
│  │   ├── perField.finish(docID)                                                     │
│  │   └── Update field generation                                                    │
│  └── Complete document processing                                                   │
└─────────────────────────────────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              Flush Trigger Check                                    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  Check flush conditions:                                                            │
│  ├── RAM buffer size exceeded?                                                      │
│  ├── Document count threshold reached?                                              │
│  ├── Explicit flush requested?                                                      │
│  └── If yes → Trigger flush process                                                 │
└─────────────────────────────────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                         IndexingChain.flush() Process                               │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  Step 1: Stored Fields                                                              │
│  ├── storedFieldsConsumer.finish(maxDoc)                                            │
│  └── storedFieldsConsumer.flush(state, sortMap)                                     │
│                                                                                     │
│  Step 2: Doc Values                                                                 │
│  ├── Collect all doc value fields                                                   │
│  └── writeDocValues(state, sortMap)                                                 │
│                                                                                     │
│  Step 3: Points (Numeric Data)                                                      │
│  ├── Collect all point fields                                                       │
│  └── writePoints(state, sortMap)                                                    │
│                                                                                     │
│  Step 4: Vectors (KNN Data)                                                         │
│  └── vectorValuesConsumer.flush(state, sortMap)                                     │
│                                                                                     │
│  Step 5: Postings (Inverted Index)                                                  │
│  ├── Collect all fields to flush                                                    │
│  ├── Create NormsProducer for field length normalization                            │
│  └── termsHash.flush(fieldsToFlush, state, sortMap, normsMergeInstance)             │
│                                                                                     │
│  Step 6: Norms (Field Length Normalization)                                         │
│  └── writeNorms(state, sortMap)                                                     │
│                                                                                     │
│  Step 7: Segment Finalization                                                       │
│  ├── Close all consumers                                                             │
│  ├── Write segment metadata                                                          │
│  └── Return sort map for segment                                                    │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## 3. Storage Layer Data Flow

```
Codec Layer Output
     │
     ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              FSDirectory Operations                                 │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  File Creation:                                                                     │
│  ├── createOutput(fileName, IOContext)                                              │
│  │   ├── ensureOpen()                                                               │
│  │   ├── maybeDeletePendingFiles()                                                  │
│  │   └── return new FSIndexOutput(fileName)                                         │
│  │                                                                                 │
│  │   FSIndexOutput:                                                                │
│  │   ├── Opens FileOutputStream                                                     │
│  │   ├── Wraps in FilterOutputStream (chunked writes)                              │
│  │   └── Manages buffered writing                                                   │
│  │                                                                                 │
│  └── Temporary File Creation:                                                       │
│      ├── createTempOutput(prefix, suffix, IOContext)                               │
│      ├── Generate unique temp file name                                             │
│      └── Create with CREATE_NEW option                                              │
│                                                                                     │
│  File Reading:                                                                      │
│  ├── openInput(fileName, IOContext)                                                 │
│  │   ├── ensureCanRead(fileName)                                                    │
│  │   ├── Check not in pendingDeletes                                               │
│  │   └── return implementation-specific IndexInput                                  │
│  │                                                                                 │
│  │   NIOFSIndexInput (NIOFSDirectory):                                             │
│  │   ├── Opens FileChannel                                                          │
│  │   ├── Uses positional read operations                                            │
│  │   └── Supports concurrent access                                                 │
│  │                                                                                 │
│  │   MemorySegmentIndexInput (MMapDirectory):                                      │
│  │   ├── Memory-maps file content                                                   │
│  │   ├── Direct memory access                                                       │
│  │   └── Efficient for large files                                                 │
│  │                                                                                 │
│  └── File Management:                                                               │
│      ├── listAll() - list directory contents                                        │
│      ├── deleteFile() - mark for deletion                                           │
│      ├── fileLength() - get file size                                               │
│      └── sync() - ensure data persistence                                           │
└─────────────────────────────────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              Physical File System                                   │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  Write Operations:                                                                  │
│  ├── Files.newOutputStream(path, options)                                           │
│  ├── StandardOpenOption.CREATE_NEW                                                  │
│  ├── StandardOpenOption.WRITE                                                       │
│  └── Chunked writes (CHUNK_SIZE = 8192 bytes)                                       │
│                                                                                     │
│  Read Operations:                                                                   │
│  ├── FileChannel.open(path, READ)                                                   │
│  ├── FileChannel.read(buffer, position)                                             │
│  └── Memory mapping via MemorySegment API                                           │
│                                                                                     │
│  File Structure on Disk:                                                           │
│  ├── Segment files (_N prefix)                                                      │
│  ├── Compound files (.cfs/.cfe)                                                     │
│  ├── Index files (.tim, .tip, .doc, .pos, etc.)                                    │
│  ├── Metadata files (.si, .fnm)                                                     │
│  └── Lock files (write.lock)                                                        │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## 4. Threading and Concurrency Model

```
Main Indexing Thread
     │
     ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                          DocumentsWriter Thread Management                          │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  Thread Allocation:                                                                 │
│  ├── flushControl.obtainAndLock()                                                   │
│  │   ├── Get available DocumentsWriterPerThread (DWPT)                             │
│  │   ├── Or create new DWPT if needed                                               │
│  │   └── Lock DWPT for exclusive access                                             │
│  │                                                                                 │
│  ├── Document Processing:                                                           │
│  │   ├── dwpt.updateDocuments(docs, analyzer)                                       │
│  │   ├── Process in IndexingChain                                                   │
│  │   └── Update RAM usage tracking                                                  │
│  │                                                                                 │
│  └── Thread Release:                                                                │
│      ├── Check if DWPT ready for flush                                              │
│      ├── perThreadPool.release(dwpt)                                                │
│      └── Return DWPT to pool or mark for flush                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              Flush Control Coordination                             │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐                │
│  │ Indexing        │    │ Flush           │    │ Merge           │                │
│  │ Threads         │    │ Threads         │    │ Threads         │                │
│  │                 │    │                 │    │                 │                │
│  │ • Add docs      │    │ • Write segments│    │ • Merge segments│                │
│  │ • Fill DWPT     │    │ • Release memory│    │ • Optimize index│                │
│  │ • Check limits  │    │ • Update index  │    │ • Background    │                │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘                │
│           │                       │                       │                        │
│           └───────────────────────┼───────────────────────┘                        │
│                                   │                                                │
│  Coordination:                    │                                                │
│  ├── FlushPolicy determines when to flush                                           │
│  ├── MergePolicy determines when to merge                                           │
│  ├── Stall control prevents memory overflow                                         │
│  └── Synchronization points for consistency                                         │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

This comprehensive flow documentation shows how Apache Lucene transforms documents from application input through to persistent storage, handling the complexities of concurrent processing, memory management, and efficient disk-based storage.