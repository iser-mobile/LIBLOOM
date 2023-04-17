package cn.edu.ruc.libloom;

import java.util.BitSet;

public class BloomBitSet {
    public BitSet bitSet;
    public int size;

    public BloomBitSet(BitSet bitSet, int size) {
        this.bitSet = bitSet;
        this.size = size;
    }
}


