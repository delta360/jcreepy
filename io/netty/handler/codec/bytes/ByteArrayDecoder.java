
package io.netty.handler.codec.bytes;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

public class ByteArrayDecoder
extends MessageToMessageDecoder<ByteBuf, byte[]> {
    public ByteArrayDecoder() {
        super(ByteBuf.class);
    }

    @Override
    public byte[] decode(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        byte[] array;
        if (msg.hasArray()) {
            if (msg.arrayOffset() == 0 && msg.readableBytes() == msg.capacity()) {
                array = msg.array();
            } else {
                array = new byte[msg.readableBytes()];
                msg.getBytes(0, array);
            }
        } else {
            array = new byte[msg.readableBytes()];
            msg.getBytes(0, array);
        }
        return array;
    }
}

