package bran;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import play.Logger;

public class QuickServer {

	public static int httpPort;
	public static int httpsPort;

	public QuickServer(String[] args) {
		httpPort = 999;

		ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
				Executors.newCachedThreadPool(), Executors.newCachedThreadPool())
				);
		try {
			bootstrap.setPipelineFactory(new QuickServerPipelineFactory());
			bootstrap.bind(new InetSocketAddress(httpPort));
			bootstrap.setOption("child.tcpNoDelay", true);
		} catch (ChannelException e) {
			Logger.error("Could not bind on port " + httpPort, e);
			System.exit(-1);
		}
	}

	public static void main(String[] args) throws Exception {
		new QuickServer(args);
	}
}
