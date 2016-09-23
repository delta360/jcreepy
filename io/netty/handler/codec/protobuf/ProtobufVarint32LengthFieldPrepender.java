/*
 * Decompiled with CFR 0_115.
 * 
 * Could not load the following classes:
 *  com.google.protobuf.CodedOutputStream
 */
package io.netty.handler.codec.protobuf;

import com.google.protobuf.CodedOutputStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.io.OutputStream;

@ChannelHandler.Sharable
public class ProtobufVarint32LengthFieldPrepender
extends MessageToByteEncoder<ByteBuf> {
    public ProtobufVarint32LengthFieldPrepender() {
        super(ByteBuf.class);
    }

    @Override
    public void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        int bodyLen = msg.readableBytes();
        int headerLen = CodedOutputStream.computeRawVarint32Size((int)bodyLen);
        out.ensureWritableBytes(headerLen + bodyLen);
        CodedOutputStream headerOut = CodedOutputStream.newInstance((OutputStream)new ByteBufOutputStream(out));
        headerOut.writeRawVarint32(bodyLen);
        headerOut.flush();
        out.writeBytes(msg, msg.readerIndex(), bodyLen);
    }
}

