
package io.netty.handler.codec.compression;

public enum ZlibWrapper {
    ZLIB,
    GZIP,
    NONE,
    ZLIB_OR_NONE;
    

    private ZlibWrapper() {
    }
}

