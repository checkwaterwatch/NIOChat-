package com.chat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Scanner;

public class NIOClient {

	private static final String SLIPT = "@@";
	private static final String USER_EXIST = "system message: user exist, please change a name";

	private ByteBuffer buffer = ByteBuffer.allocate(1024);

	private int port;
	private String addr,name="",line="";
	private Selector selector;
	private SocketChannel socketChannel;
	private Charset charset = Charset.forName("UTF-8");

	public NIOClient(int port, String addr) {
		this.port = port;
		this.addr = addr;
		init();
	}

	public void init() {
		try {
			selector = Selector.open();
			socketChannel = SocketChannel.open();
			socketChannel.configureBlocking(false);
			socketChannel.register(selector, SelectionKey.OP_CONNECT);
			socketChannel.connect(new InetSocketAddress(addr, port));
			

			// 开启一个线程读从服务端的数据
			new Thread(new ClientThread()).start();
			
			//主线程输入
			Scanner scanner = new Scanner(System.in);
			String send;
			while(scanner.hasNextLine()){
				send = scanner.nextLine();
				if("".equals(send)) continue;
				if("".equals(name)) {
					name = send; //如果名字未注册默认第一行为名字
				    send = name+SLIPT;  //发送的格式 名字+@@ /+内容
				}else send = name+SLIPT+send;
				
				socketChannel.write(charset.encode(send));
				
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private class ClientThread implements Runnable {
		public void run() {
			while (true) {
				int n;
				try {
					n = selector.select();
					if (n == 0)
						continue;
					Iterator iterator = selector.selectedKeys().iterator();
					while (iterator.hasNext()) {
						SelectionKey key = (SelectionKey) iterator.next();
						iterator.remove();
						dealData(key);
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}

		}

		private void dealData(SelectionKey key)  {
			if (key.isConnectable()) {
				SocketChannel sChannel=(SocketChannel) key.channel();
				if (sChannel.isConnectionPending()) {
					try {
						sChannel.finishConnect();
						sChannel.register(selector, SelectionKey.OP_READ);//非常重要！！！ 原代码是key.interestOps(OP_READ)
					} catch (ClosedChannelException e) {
						System.out.println("ClosedChannelException   服务器关闭!");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					System.out.println("连接成功!");
				}
			}
			if(key.isValid()&& key.isReadable()){
			SocketChannel sChannel=(SocketChannel) key.channel();
			StringBuilder content = new StringBuilder();
			int n;
			buffer.clear();
			try {
				while((n = sChannel.read(buffer))>0){
					buffer.flip();
					content.append(charset.decode(buffer));
				}
			} catch (IOException e) {
				key.cancel();
				try {
					sChannel.socket().close();
					sChannel.close();
				} catch (IOException e1) {
					System.out.println("IOException   服务器关闭!");
				}
			}
			if (USER_EXIST.equals(content)) 
				name="";
			
			System.out.println(content);
			
		}
		}
	}

	public static void main(String[] args) {
		new NIOClient(8888, "169.254.126.36");
	}
}
