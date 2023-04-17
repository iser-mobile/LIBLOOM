package cn.edu.ruc.libloom;

import com.google.common.hash.Hashing;
import com.google.common.primitives.Longs;

import java.nio.charset.Charset;

public class BloomHash {
    private final int cap;
    private final int numHashFunctions;

    public BloomHash(int cap, int numHashFunctions){
        this.cap = cap;
        this.numHashFunctions = numHashFunctions;
    }

    public int[] hash(String value){
        int[] position = new int[numHashFunctions];
        byte[] bytes = Hashing.murmur3_128().hashString(value, Charset.defaultCharset()).asBytes();
        long hash1 = lowerEight(bytes);
        long hash2 = upperEight(bytes);
        long combinedHash = hash1;
        for (int i=0; i<numHashFunctions; i++){
            position[i] = (int)(Math.abs(combinedHash) % cap);
            combinedHash += hash2;
        }
        return position;
    }

    private long upperEight(byte[] bytes){
        return Longs.fromBytes(bytes[15],bytes[14],bytes[13],bytes[12],bytes[11],bytes[10],bytes[9],bytes[8]);
    }
    private long lowerEight(byte[] bytes){
        return Longs.fromBytes(bytes[7],bytes[6],bytes[5],bytes[4],bytes[3],bytes[2],bytes[1],bytes[0]);
    }
}