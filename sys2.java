import java.io.*;
import java.util.Scanner;


/**
 * Created by Felix on 4/16/2017.
 */

public class sys2 {
    final static int blockSz = 16, missPenalty = 80, wordBytes = 4, maxLines = 100000;//Set block size of 16 bytes.
    static int cacheSz, setAssociative, numSets, wordsPerRow, ic1, ic2, lineNum = 0, offsetBits, indexBits,
            tagbits, diff, loads = 0, stores = 0, wMiss = 0, rMiss = 0, dRmiss = 0, dWmiss = 0,
            bytesRead =0, bytesWritten = 0,rTime = 0, wTime = 0;
    static boolean verbose = false;
    static String dataCache[][];
    static String verboseOutPut = "";

    public static void main( String[] args){
        File file;
        PrintWriter pw;
        Scanner scan, lineScanner;
        Boolean baseChange;

        try{
            pw = new PrintWriter("myOut.txt", "UTF-8");//results

            setup(args);
            file = new File(args[0]);
            scan = new Scanner(file);

            scan.next();
            scan.next();
            String temp = scan.next().substring(2); //3rd value on the line is the address to analyze.

            diff = getDifferenceToIndexChange(temp.substring(2));

            scan.close();//reset
            scan = new Scanner(file);

            int bytes, blockIndex = -1;
            Long addr, baseAddr = 0L, indexedTo;
            String binString, data, curTag, cacheTag;
            boolean isRead, isValid = false, isDirty = false, found, emptyBlockAvailable;

            baseChange = false;
            while(scan.hasNextLine()){
                String line = scan.nextLine(), addrStr;
                int firstEmptyBlock = maxLines, blockToReplace = maxLines, lastUsed = maxLines;
                found = false;
                emptyBlockAvailable = false;

                if(line.isEmpty())//last line is empty in file.
                    break;

                lineScanner = new Scanner(line);

                lineScanner.next();//Skip PC address

                isRead = (lineScanner.next().equals("R"))? true:false;
                addrStr = lineScanner.next();
                binString = hexToBinString( addrStr );//remove preceding '0x' from mem address.
                indexedTo = findIndex(binString);
                curTag = Long.toHexString(findTag(binString));
                bytes = Integer.parseInt( lineScanner.next() );
                data = lineScanner.next();

                if(verbose && (lineNum >= ic1 && lineNum <= ic2) ){
                    verboseOutPut = lineNum + " " + Long.toHexString(indexedTo) + " " + curTag + " ";
                }

                /* These need to be done in the threads
                isValid = (dataCache[indexedTo.intValue()][0].charAt(0) == '1')? true:false;
                isDirty = (dataCache[indexedTo.intValue()][0].charAt(2) == '1')? true:false;
                */

                for(int block = 0; block < setAssociative; block++){
                    String strToPass = dataCache[indexedTo.intValue()][block];
                    SearchBlocks search = new SearchBlocks(block, strToPass, curTag);
                    search.run();
                    int tempAnswer = search.getBlockIndexToReturn();

                    if(tempAnswer != -1){//Found a block matching tags
                        blockIndex = tempAnswer;
                        found = true;
                    }
                    else if(!search.isValid() && block < firstEmptyBlock && !found){
                        firstEmptyBlock = block;
                        emptyBlockAvailable = true;
                        blockIndex = firstEmptyBlock;

                    }
                    else if(search.isValid() && !emptyBlockAvailable && !found){//Search for block with lowest last-used int
                        if(search.getLastUsed() < lastUsed){
                            lastUsed = search.getLastUsed();
                            blockToReplace = block;
                            blockIndex = blockToReplace;
                        }
                    }
                }


                String lastUsedStr, record = dataCache[indexedTo.intValue()][ blockIndex],
                        cacheTagHexStr;
                Scanner cacheScan = new Scanner(record);
                int length;

                isValid = (cacheScan.next().equals("1"))? true:false;
                isDirty = (cacheScan.next().equals("1"))? true:false;

                lastUsedStr = cacheScan.next();
                length = lastUsedStr.length();

                lastUsed = Integer.parseInt(lastUsedStr);
                cacheTag = cacheScan.next();


                if(verbose && (lineNum >= ic1 && lineNum <= ic2) ){
                    verboseOutPut += (isValid)? "1":"0";
                    verboseOutPut += " " + blockIndex + " " + lastUsedStr + " " + cacheTag + " ";
                    verboseOutPut += (isDirty)? "1 ":"0 ";
                }

                if(found){
                    //process hits
                    if(verbose && (lineNum >= ic1 && lineNum <= ic2) ){
                        verboseOutPut += "1 1 \n";//indicates a hit | indicates case 1.
                    }
                    if(isRead){
                        loads++;
                        rTime++;

                        //replace last-used field with new field;
                        dataCache[indexedTo.intValue()][blockIndex] =  record.substring(0, 4) + lineNum + record.substring(4 + length);
                    }else{
                        stores++;
                        wTime++;

                        //Dirty bit set to on.
                        record = record.substring(0, 2) + "1" + record.substring(3);
                        //replace last-used field with new field;
                        dataCache[indexedTo.intValue()][blockIndex] =  record.substring(0, 4) + lineNum + record.substring(4 + length);
                    }
                }
                else{
                    //process misses
                    if(verbose && (lineNum >= ic1 && lineNum <= ic2) ){
                        verboseOutPut += "0 ";//indicates a miss.
                    }

                    int blockToUse = -1;

                    if(emptyBlockAvailable || !isDirty){//2a

                        if(verbose && (lineNum >= ic1 && lineNum <= ic2) ){
                            verboseOutPut += "2a\n";
                        }
                        blockToUse = (emptyBlockAvailable)? firstEmptyBlock:blockToReplace;
                        bytesRead += bytes;

                        if(isRead){
                            dataCache[indexedTo.intValue()][blockToUse] =  "1 0 " + lineNum + " " + curTag;
                            dRmiss++;
                            loads++;
                            rTime += (1 + missPenalty);

                        }
                        else{
                            dataCache[indexedTo.intValue()][blockToUse] =  "1 1 " + lineNum + " " + curTag;
                            dWmiss++;
                            stores++;
                            wTime += (1 + missPenalty);
                        }

                    }
                    else{//2b
                        if(verbose && (lineNum >= ic1 && lineNum <= ic2) ){
                            verboseOutPut += "2b\n";
                        }
                        blockToUse = blockToReplace;

                        if(isRead){
                            bytesWritten += bytes;
                            bytesRead += bytes;
                            loads++;
                            rTime += (1 + (2*missPenalty) );

                            dataCache[indexedTo.intValue()][blockToUse] =  "1 0 " + lineNum + " " + curTag;
                        }
                        else{
                            bytesWritten += bytes;
                            bytesRead += bytes;
                            stores++;
                            wTime += (1 + (2*missPenalty) );

                            dataCache[indexedTo.intValue()][blockToUse] =  "1 1 " + lineNum + " " + curTag;
                        }

                    }


                }



/*
                if(verbose && (lineNum >= ic1 && lineNum <= ic2) ){
                    verboseOutPut = lineNum + " " + Long.toHexString(indexedTo) + " " + Long.toHexString(curTag) + " " +
                            dataCache[indexedTo.intValue()][0].charAt(0) + " " + Long.toHexString(cacheTag) +
                            " " + dataCache[indexedTo.intValue()][0].charAt(2) + " ";
                }

*/
                if(verbose && (lineNum >= ic1 && lineNum <= ic2) ){
                    System.out.print(verboseOutPut);
                    pw.print(verboseOutPut);
                }

                lineNum++;
            }

            String output = setAssociative +"-way, writeback, size = " + args[1] + "KB\n";
            output += "loads " + loads + " stores " + stores + " total " + (loads+stores) + "\n";
            output += "rmiss: " + rMiss + " wmiss: " + wMiss + " total " + (rMiss+wMiss) + "\n";
            output += "dirty rmiss: " + dRmiss + " dirty wmiss: " + dWmiss + "\n";
            output += "bytes read " + bytesRead + " bytes written " + bytesWritten + "\n";
            output += "read time " + rTime + " write time " + wTime + "\n";
            output += "total time " + (rTime + wTime) + "\n";
            output += "miss rate " + (rMiss+wMiss)/((double)loads+stores) + "\n";

            System.out.print(output );
            pw.print(output);
            System.out.println("sys2");
            pw.close();

        }catch(FileNotFoundException exception)
        {
            System.out.println("Could not locate file: " + args[0]);
        }
        catch(Exception exception)
        {
            System.out.println("Error: " + exception.getMessage());
            exception.printStackTrace();
        }
    }

    static void setup(String[] arguments)throws Exception{

        if(arguments.length == 3 || arguments.length == 8){
            cacheSz = Integer.parseInt( arguments[1] ) * (int)Math.pow(2, 10);//cacheSz set to bytes
            System.out.println("Cache size set to: " + cacheSz);
        }else{
            throw new Exception("invalid number of arguments");
        }

        if( (cacheSz & (cacheSz -1) ) != 0)
            throw new Exception("Cache Size must be a power of 2");
        else if(arguments.length == 8 && !arguments[3].equals("["))
            throw new Exception("invalid argument " + arguments[3]);
        else if(arguments.length == 8 && !arguments[7].equals("]"))
            throw new Exception("invalid argument " + arguments[7]);
        else if(arguments.length == 8 && !arguments[4].equals("-v"))
            throw new Exception("Invalid argument: " +arguments[4]);
        else if (arguments.length == 8 && Integer.parseInt(arguments[5]) > Integer.parseInt(arguments[6]))
            throw new Exception("Start value : " + arguments[5] + " must be less than or equal to end value: " + arguments[6]);


        if(arguments.length == 8 && arguments[4].equals("-v")){
            verbose = true;
            ic1 = Integer.parseInt(arguments[5]);
            ic2 = Integer.parseInt(arguments[6]);
        }

        setAssociative = Integer.parseInt(arguments[2]);

        double temp;
        numSets = cacheSz/(setAssociative);
        System.out.println("Number of sets: " + numSets);

        temp = Math.log(blockSz)/Math.log(2);
        offsetBits = (int)temp;
        System.out.println("offset bits: " + offsetBits);

        temp = Math.log(cacheSz/(blockSz*setAssociative))/Math.log(2);
        indexBits = (int)temp;
        System.out.println("index bits: " + indexBits);

        tagbits = 28 - indexBits - offsetBits;//Should be 32bits but the first four are 0000
        wordsPerRow = blockSz/wordBytes;
        dataCache = new String[numSets][setAssociative];
/*
        SearchBlocks search1;
        for(int i = 0; i < 3; i++){
            search1 = new SearchBlocks(i);
            search1.run();
        }
*/
        for(int i = 0; i < numSets; i++){
            //setup: [i][j] = "validBit dirtyBit last-used Tag"  | last-used refers to the line number from the file starting with line 0.
            for(int j = 0; j < setAssociative; j++){
                dataCache[i][j] = "0 0 0 0";
            }
        }
    }

    static int getDifferenceToIndexChange(String pcAddr){
        String hexString, tempStr;
        Long tempIndex, num, tempNum, baseIndex;
        int difference = 0, multiple = 5;

        hexString = pcAddr.substring(0, pcAddr.length()-1);//remove last digit.
        hexString += '0';//Start with 0 at end to find the number that changes the index.
        num = Long.parseLong(hexString, 16);
        baseIndex = findIndex(Long.toBinaryString(num));

        for(int i = 1; i < (17 * multiple); i++){
            tempNum = num + i;
            tempIndex = findIndex(Long.toBinaryString(tempNum));

            if(baseIndex != tempIndex){//Find when a change in index is found.
                difference = i;
                break;
            }
        }

        return difference;
    }


    static String hexToBinString(String hexString){

        return Long.toBinaryString( Long.parseLong(hexString.substring(2), 16) );
    }

    static Long findIndex(String binAddrStr){

        return Long.parseLong(binAddrStr.substring(binAddrStr.length() - indexBits - offsetBits, binAddrStr.length() - offsetBits), 2);
    }

    static Long findTag(String binAddrStr){

        return  Long.parseLong(binAddrStr.substring(0, binAddrStr.length() - indexBits - offsetBits), 2);
    }


}

class SearchBlocks implements Runnable{
    int blockToParse, blockIndexToReturn = -1;
    private int lastUsed = -1;
    String strToParse, tagToFind,cacheTag;
    private boolean valid, dirty;

    SearchBlocks(int blockToSearch, String strToParse, String tagToFind){
        blockToParse = blockToSearch;
        this.strToParse = strToParse;
        this.tagToFind =tagToFind;
        //System.out.println("Created Thread to search " + blockToParse);
    }

    public void run(){
        //System.out.println("Running search of [" + blockToParse + "]");
        /*
        Block    valid         Dirty      Last-Used   Tag
          0   |   0/1          0/1         line#     2ff473  |
         */
        try {
            Scanner lineScan = new Scanner(strToParse);

            valid = (lineScan.next().equals("1"))? true:false;

            dirty = (lineScan.next().equals("1") )? true:false;//dirty bit
            lastUsed = Integer.parseInt( lineScan.next() );//last-used

            cacheTag = lineScan.next();

            if( isValid() && tagToFind.equals(cacheTag)){
                //System.out.println("found block");
                blockIndexToReturn = blockToParse;
            }

            // Let the thread sleep for a while.
            Thread.sleep(0);

        }catch (InterruptedException e) {
            System.out.println("SearchBlock " +  blockToParse + " interrupted.");
        }
        //System.out.println("SearchBlock: " + blockToParse + " ended.");
    }

    //Returns the block index where a match was found otherwise returns -1
    public int getBlockIndexToReturn(){
        return blockIndexToReturn;
    }

    public int getLastUsed() {
        return lastUsed;
    }

    public String getCacheTag() {
        return cacheTag;
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isDirty() {
        return dirty;
    }

}

