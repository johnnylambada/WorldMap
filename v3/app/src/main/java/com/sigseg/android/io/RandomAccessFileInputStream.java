// *** WARNING ***
// This piece of code is guaranteed incorrect and not doing what you
// expect - it has been deliberately botched to work around bugs in 
// other software.

package com.sigseg.android.io;

import java.io.File;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import android.util.Log;

public class RandomAccessFileInputStream extends InputStream {

    public static  int DEFAULT_BUFFER_SIZE = 16 * 1024;
    RandomAccessFile fp;
    long markPos=-1;
    long fileLength = -1;
        String TAG="WorldMapActivityRAIFS";
    
    public RandomAccessFileInputStream(File file, int bufferSize)
            throws FileNotFoundException {
        fp = new RandomAccessFile(file, "r");
        try {
            fileLength = fp.length();
        } catch(IOException e) { Log.e(TAG, e.getMessage()); }
        Log.d(TAG,"opened, len = "+fileLength);
    }
    
    public int available() {
        long pos=0;
        int res;
        try {
        pos=fp.getFilePointer();
        } catch(IOException e){ Log.e(TAG, "available "+e.getMessage()); }
        res = (int)(fileLength - pos);
        Log.d(TAG,"available "+res);
        return res;
    }
    
    public RandomAccessFileInputStream(File file)
    throws FileNotFoundException {
        this(file, DEFAULT_BUFFER_SIZE);
    }

    public RandomAccessFileInputStream(String filename)
    throws FileNotFoundException {
        this(new File(filename), DEFAULT_BUFFER_SIZE);
    }


    @Override
    public int read() throws IOException {
        int res=fp.read();
        //Log.d(TAG,"read single byte, res "+res);
        return res;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int res=fp.read(b, off, len);
        // Log.d(TAG,"read bol, res "+res);
        return res;
    }
    
    @Override
    public int read(byte[] b) throws IOException {
        int res=fp.read(b);
        //Log.d(TAG,"read buf, res "+res);
        return res;
    }
    
    public void close() throws IOException {
        fp.close();
    }
    
    
    public int skip(int n) throws IOException {
        int res=fp.skipBytes(n);
        long pos=fp.getFilePointer();
        Log.d(TAG,"skip "+n+" res "+res+" pos now "+pos);
        return res;
    }
    
    public void mark(int readLimit) {
        try {
        markPos = fp.getFilePointer();
        /* attempted workaround that did not work */
        /*
        if (markPos >= fileLength) {
            markPos=0;
            Log.d(TAG,"mark at EOF requested - setting mark at 0 instead");
        }
        */
        } catch (IOException e) {
        Log.e(TAG, e.getMessage());
        }
        Log.d(TAG,"mark at "+markPos+" readLimit "+readLimit);
    }
      
    public void reset() throws IOException {
        long oldpos=fp.getFilePointer();
        //fp.seek(markPos);
        // apparently the only things that works is to reset to zero
        // regardless of markPos
        fp.seek(0);
        long pos=fp.getFilePointer();
        Log.d(TAG,"reset oldPos"+oldpos+" to "+markPos+" resulting pos "+pos);
    }       

    public boolean markSupported() {
        return true;
    }
}
