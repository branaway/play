package bran;

import static org.jboss.netty.channel.Channels.pipeline;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;

public class QuickServerPipelineFactory implements ChannelPipelineFactory {

    @Override
	public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = pipeline();
        
        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("encoder", new HttpResponseEncoder());
        pipeline.addLast("handler", new QuickHandler());

        return pipeline;
    }
}

