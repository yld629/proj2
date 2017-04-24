import java.io.*;
import java.util.Scanner;


/**
 * Created by Felix on 4/16/2017.
 */

public class sys1 {
    final static int blockSz = 16, missPenalty = 4, wordBytes = 4;//Set block size of 16 bytes.
    static int cacheSz, numRows, wordsPerRow, ic1, ic2, lineNum = 0, offsetBits, indexBits,
            tagbits, diff, loads = 0, stores = 0, wMiss = 0, rMiss = 0, dRmiss = 0, dWmiss = 0,
            rTime = 0, wTime = 0;
    static boolean verbose = false;
    static String dataCache[][];

    public static void main( String[] args){
        File file;
        Scanner scan, lineScanner;
        Boolean baseChange;

        try{
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
            String binString, data;
            boolean isRead, isValid;

            baseChange = false;
            while(scan.hasNextLine()){
                String line = scan.nextLine();

                //System.out.println("Line no: " + lineNum + "\t" + line);

                if(line.isEmpty())//last line is empty in file.
                    break;

                lineScanner = new Scanner(line);

                lineScanner.next();//Skip PC address

                isRead = (lineScanner.next().equals("R"))? true:false;
                binString = hexToBinString( lineScanner.next() );//remove preceding '0x' from mem address.
                indexedTo = findIndex(binString);
                curTag = findTag(binString);
                bytes = Integer.parseInt( lineScanner.next() );
                data = lineScanner.next();
                isValid = (dataCache[indexedTo.intValue()][0].charAt(0) == '1')? true:false;

                /* System.out.println("Address: " + temp + " indexed to: "
                        + Long.toHexString(indexedTo) + " with tag: " + Long.toHexString(curTag)+"\n" );
*/
                addr = Long.parseLong( temp, 16);
                temp = temp.substring(0, temp.length() -1) + '0'; //remove last digit and add 0 to use as a baseline.

                Long curBaseAddr = Long.parseLong( temp , 16);

                if(curBaseAddr != baseAddr){
                    baseChange = true;
                    baseAddr = curBaseAddr;
                }else
                    baseChange = false;

                //setup: [0] = "validBit dirtyBit Tag"  [1] = data;
                if(baseChange || addr - baseAddr >= diff){//Detects a change in index from previous mem address/ or is first address.
                    cacheTag = Long.parseLong(dataCache[indexedTo.intValue()][0].substring(4) );

                    //Catch instance when index is valid.
                    if(cacheTag.equals(curTag)){
                        //processHit();
                        if(isRead) {
                            loads++;

                        }
                        else {
                            stores++;
                        }
                    }else{
                        //processMiss();
                        if(isRead) {
                            loads++;
                            rMiss++;
                            dRmiss = (dataCache[indexedTo.intValue()][0].charAt(2) == '1')? dRmiss + 1 : dRmiss;
                        }
                        else {
                            stores++;
                            wMiss++;
                            dWmiss = (dataCache[indexedTo.intValue()][0].charAt(2) == '1')? dWmiss + 1 : dWmiss;
                        }
                    }
                }
                else{
                    if( isRead && dataCache[indexedTo.intValue()][0].substring(0,1).equals("1")){//read hit case 1. Update stats.
                        //just update stats
                        loads++;
                        rTime++;

                    }
                    else if( !isRead ){//case 1 write hit.
                        //processHit();
                        stores++;
                    }
                }

                lineNum++;
            }
            System.out.println("Loads: " + loads + " stores: " + stores + " total accesses: " + (loads+stores));
            System.out.println("rmiss: " + rMiss + " wmiss: " + wMiss);

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
        else if (arguments.length == 7 && Integer.parseInt(arguments[5]) > cacheSz)
            throw new Exception("End value: " + arguments[5] + " is greater than cache size");

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

