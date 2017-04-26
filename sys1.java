import java.io.*;
import java.util.Scanner;


/**
 * Created by Felix on 4/16/2017.
 */

public class sys1 {
    final static int blockSz = 16, missPenalty = 4, wordBytes = 4;//Set block size of 16 bytes.
    static int cacheSz, numRows, wordsPerRow, ic1, ic2, lineNum = 0, offsetBits, indexBits,
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

            int bytes, hits = 0, misses= 0;
            Long addr, baseAddr = 0L, mem_addr = 0L, indexedTo, curTag, cacheTag;
            String binString, data, indexedToStr, curTagStr, cacheTagStr ;
            boolean isRead, isValid, isDirty;

            baseChange = false;
            while(scan.hasNextLine()){
                String line = scan.nextLine(), addrStr;

                //System.out.println("Line no: " + lineNum + "\t" + line);

                if(line.isEmpty())//last line is empty in file.
                    break;

                lineScanner = new Scanner(line);

                lineScanner.next();//Skip PC address

                isRead = (lineScanner.next().equals("R"))? true:false;
                addrStr = lineScanner.next();
                binString = hexToBinString( addrStr );//remove preceding '0x' from mem address.
                indexedTo = findIndex(binString);
                indexedToStr = Long.toHexString(indexedTo);
                curTag = findTag(binString);
                curTagStr = Long.toHexString(curTag);
                bytes = Integer.parseInt( lineScanner.next() );
                data = lineScanner.next();
                isValid = (dataCache[indexedTo.intValue()][0].charAt(0) == '1')? true:false;
                isDirty = (dataCache[indexedTo.intValue()][0].charAt(2) == '1')? true:false;
                cacheTag = Long.parseLong( dataCache[indexedTo.intValue()][0].substring(4) );
                cacheTagStr = Long.toHexString(cacheTag);

                if(verbose && (lineNum >= ic1 && lineNum <= ic2) ){
                    verboseOutPut = lineNum + " " + Long.toHexString(indexedTo) + " " + Long.toHexString(curTag) + " " +
                            dataCache[indexedTo.intValue()][0].charAt(0) + " " + Long.toHexString(cacheTag) +
                            " " + dataCache[indexedTo.intValue()][0].charAt(2) + " ";
                    //System.out.print(verboseOutPut);
                    //pw.print(verboseOutPut);
                }
                /* System.out.println("Address: " + temp + " indexed to: "
                        + Long.toHexString(indexedTo) + " with tag: " + Long.toHexString(curTag)+"\n" );
*/
                addr = Long.parseLong( addrStr.substring(2), 16);//remove "0x"
                addrStr = addrStr.substring(2, addrStr.length() -1) + '0'; //remove last digit and add 0 to use as a baseline.

                Long curBaseAddr = Long.parseLong( addrStr , 16);

                if(!curBaseAddr.equals(baseAddr )){
                    baseChange = true;
                    baseAddr = curBaseAddr;
                }else
                    baseChange = false;

                /*setup: [0] = "validBit dirtyBit Tag"  [1] = data;
                Detects a change in index from previous mem address ||  is first address || tags don't match a valid cache index.*/
                if(baseChange && !isValid || addr - baseAddr >= diff && !isValid
                        || addr - baseAddr >= diff && !isValid && cacheTag != curTag
                        || !cacheTag.equals(curTag) && isValid){
                    //processMiss();
                    String placeHolder;
                    if(verbose && (lineNum >= ic1 && lineNum <= ic2))
                        verboseOutPut += "0 ";//set '0' to indicate a miss in verbose output.

                    placeHolder = dataCache[indexedTo.intValue()][0].substring(0, 4);//remove old tag and insert new
                    dataCache[indexedTo.intValue()][0] = placeHolder + curTag;

                    bytesRead += bytes;//mem ->cache happens for all cases of miss.
                    dataCache[indexedTo.intValue()][1] = data;

                    placeHolder = dataCache[indexedTo.intValue()][0].substring(1);//replace valid bit
                    dataCache[indexedTo.intValue()][0] = '1' + placeHolder;

                    if(isDirty){
                        bytesWritten += bytes;//data in cache --->Memory. Happens for all dirty misses.

                        if(verbose && (lineNum >= ic1 && lineNum <= ic2))
                            verboseOutPut += "2b\n";
                        //insert cycle counts for dirty misses
                    }
                    else{
                        if(verbose && (lineNum >= ic1 && lineNum <= ic2))
                            verboseOutPut +="2a\n";
                        //insert cycle counts for clean misses
                    }


                    if(isRead) {
                        loads++;
                        rMiss++;
                        dRmiss = (dataCache[indexedTo.intValue()][0].charAt(2) == '1')? dRmiss + 1 : dRmiss;

                        //Set dirtybit to clear
                        placeHolder = dataCache[indexedTo.intValue()][0];
                        dataCache[indexedTo.intValue()][0] = placeHolder.substring(0, 2) + '0' +
                                placeHolder.substring(3);
                    }
                    else {
                        stores++;
                        wMiss++;
                        dWmiss = (dataCache[indexedTo.intValue()][0].charAt(2) == '1')? dWmiss + 1 : dWmiss;

                        //Set dirtybit to dirty
                        placeHolder = dataCache[indexedTo.intValue()][0];
                        dataCache[indexedTo.intValue()][0] = placeHolder.substring(0, 2) + '1' +
                                placeHolder.substring(3);
                    }

                }
                else{
                    //process hits
                    String placeHolder;

                    if(verbose && (lineNum >= ic1 && lineNum <= ic2))
                        verboseOutPut += "1 1 \n";//set "1 1" to indicate a hit in verbose output of type case 1.

                    if( isRead && dataCache[indexedTo.intValue()][0].substring(0,1).equals("1")){//read hit case 1. Update stats.
                        //just update stats
                        loads++;
                        rTime++;

                    }
                    else if( !isRead ){//case 1 write hit.
                        stores++;

                        //Set dirty bit
                        placeHolder = dataCache[indexedTo.intValue()][0];
                        dataCache[indexedTo.intValue()][0] = placeHolder.substring(0, 2) + '1' +
                                placeHolder.substring(3);

                        //mem ->cache happens for all cases of write-hits.
                        bytesRead += bytes;
                        dataCache[indexedTo.intValue()][1] = data;
                    }
                }
                if(verbose && (lineNum >= ic1 && lineNum <= ic2)){
                    System.out.print(verboseOutPut);
                    pw.print(verboseOutPut);
                }

                lineNum++;
            }
            //System.out.println(verboseOutPut);
            System.out.println("Loads: " + loads + " stores: " + stores + " total accesses: " + (loads+stores));
            System.out.println("rmiss: " + rMiss + " wmiss: " + wMiss);

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

    static void processMiss(boolean dirtyBitSet, boolean readSet, int bytesToWrite){
            if(dirtyBitSet){

            }

    }

    static void setup(String[] arguments)throws Exception{

        if(arguments.length == 2 || arguments.length == 7){
            cacheSz = Integer.parseInt( arguments[1] ) * (int)Math.pow(2, 10);//cacheSz set to bytes
            System.out.println("Cache size set to: " + cacheSz);
        }else{
            throw new Exception("invalid number of arguments");
        }

        if( (cacheSz & (cacheSz -1) ) != 0)
            throw new Exception("Cache Size must be a power of 2");
        else if(arguments.length == 7 && !arguments[2].equals("["))
            throw new Exception("invalid argument " + arguments[2]);
        else if(arguments.length == 7 && !arguments[6].equals("]"))
            throw new Exception("invalid argument " + arguments[6]);
        else if(arguments.length == 7 && !arguments[3].equals("-v"))
            throw new Exception("Invalid argument: " +arguments[3]);
        else if (arguments.length == 7 && Integer.parseInt(arguments[4]) > Integer.parseInt(arguments[5]))
            throw new Exception("Start value : " + arguments[4] + " must be less than or equal to end value: " + arguments[5]);


        if(arguments.length == 7 && arguments[3].equals("-v")){
            verbose = true;
            ic1 = Integer.parseInt(arguments[4]);
            ic2 = Integer.parseInt(arguments[5]);
        }

        double temp;
        numRows = cacheSz/blockSz;

        temp = Math.log(blockSz)/Math.log(2);
        offsetBits = (int)temp;
        System.out.println("offset bits: " + offsetBits);

        temp = Math.log(numRows)/Math.log(2);
        indexBits = (int)temp;
        System.out.println("index bits: " + indexBits);

        tagbits = 28 - indexBits - offsetBits;//Should be 32bits but the first four are 0000
        wordsPerRow = blockSz/wordBytes;
        dataCache = new String[numRows][2];

        for(int i = 0; i < numRows; i++){
            //setup: [i][0]: [0] = "validBit dirtyBit Tag"  [1] = data;
            dataCache[i][0] = "0 0 0";//Indicates a invalid
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

