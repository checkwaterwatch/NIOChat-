package com.chat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;



/**
 * 1.要求客户端写名字。之后客户端每次返回格式 名字+@@+内容
 * 2.客户端发送消息先经过服务端，再由服务端转发给各个客户端
 * 3.使用线程池处理数据
 * @author lenovo
 *
 */

public class NIOServer {

	private static final String SLIPT="@@";
	private static final String USER_EXIST = "system message: user exist, please change a name";
	
	private ByteBuffer rBuffer = ByteBuffer.allocate(1024);//读
	private ByteBuffer wBuffer = ByteBuffer.allocate(1024);//写
	
	//线程池
	private ThreadPoolExecutor executor = new ThreadPoolExecutor(3, 3, 0, TimeUnit.MILLISECONDS,new LinkedBlockingDeque< Runnable>()
			                                                                ,new ThreadPoolExecutor.CallerRunsPolicy());
	
	private Charset charset = Charset.forName("UTF-8");
	private  Selector selector;
//	private ServerSocketChannel serverSocketChannel;
	private HashMap<SocketChannel,String> clientMap = new HashMap<>();
	private int port;
	public NIOServer(int port){
		this.port = port;
		init();
	}
	public void init() {
		try {
			selector = selector.open();
			ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
			serverSocketChannel.socket().bind(new InetSocketAddress(port));
			serverSocketChannel.configureBlocking(false);
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
			System.out.println("server start.............");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void listen() {
		while(true){
			try {
				int count =selector.select();//等待通道有io    （ 第一次有结果肯定是server的accept，所以key是server的）
				if(count ==0) continue;
				Set selected = selector.selectedKeys();
				Iterator iterator = selected.iterator();
				while(iterator.hasNext()){
					SelectionKey key =(SelectionKey) iterator.next();
					iterator.remove();				
					//executor.execute(new ServerThread(key));
					dealData(key);
					
				}
				
			} catch (ClosedChannelException e) {
				e.printStackTrace();
			}catch (IOException e) {
				// TODO: handle exception
			}
		}
	}
	
	public class ServerThread implements Runnable{

		SelectionKey selectionKey;
		public ServerThread(SelectionKey selectionKey) {
	
			this.selectionKey = selectionKey;
		}
		
		@Override
		public void run() {
			dealData(selectionKey);
			
		}
		
	}
	
	private void dealData( SelectionKey sk)  {
		
		if (sk.isAcceptable()) {//注册
			ServerSocketChannel ssc=(ServerSocketChannel) sk.channel();
			//SocketChannel socketChannel = ssc.accept();
			try {
				SocketChannel socketChannel = ssc.accept();
				socketChannel.configureBlocking(false);
				socketChannel.register(selector, SelectionKey.OP_READ);
				
				//sk.interestOps(SelectionKey.OP_ACCEPT);//对应的继续准备接受客户端连接
				
				socketChannel.write(getBuffer("please input your name: "));
				System.out.println("Server is listening from client :" + socketChannel.getRemoteAddress());
			} catch (ClosedChannelException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}else if (sk.isValid()&&sk.isReadable()) {//客户端
			
			//读取socket数据
			SocketChannel scChannel= (SocketChannel) sk.channel();
			
			StringBuilder content = new StringBuilder();
			int n;
			rBuffer.clear();
			
			try {
				while((n=scChannel.read(rBuffer))>0) {
					rBuffer.flip();
					content.append(charset.decode(rBuffer));
				  
				}
			} catch (IOException e) { //客户端关闭时，取消相应通道
				sk.cancel();
				SocketChannel sChannel= (SocketChannel) sk.channel();
				try {
					System.out.println("Server is listening from client : " + sChannel.getRemoteAddress()+" 下线了");
					sendOtherClient(selector, sChannel, clientMap.get(sChannel)+" 下线了!");
					scChannel.socket().close();
					scChannel.close();
					
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
			
			
			try {
				System.out.println("Server is listening from client :" + scChannel.getRemoteAddress()+" data is "+content);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			//处理数据
			if (content.length() >0) {
				String[] strings = content.toString().split(SLIPT);
				//注册，输入名字 name@@
				if (strings !=null&&strings.length ==1) {
					String name = strings[0];
					if (clientMap.containsKey(name)) {
						try {
							scChannel.write(charset.encode(USER_EXIST));
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}else{
						clientMap.put(scChannel,strings[0] ); //添加用户名
						String message = "welcome "+name+" to chat room! Online numbers:"+this.OnlineNum(selector);
						sendAllClient(selector,message);//提醒所有客户端有人上线了
					    
					}
				}else if (strings !=null&&strings.length ==2) {//聊天内容 name@@content
					String name = strings[0];
					String mString = name+" : "+strings[1];
					if (clientMap.containsKey(name)) 
						sendOtherClient(selector, scChannel, mString);
				}
			}
		}
	
}
	
	private ByteBuffer getBuffer(String string) {
		wBuffer.clear();
		wBuffer.put(charset.encode(string));
		wBuffer.flip();
		return wBuffer;
	}
	public void sendAllClient(Selector selector,String msg) {
		sendOtherClient(selector, null, msg);
	}
	
	public void sendOtherClient(Selector selector,SocketChannel owner,String msg) {
		 
		for(SelectionKey sk : selector.keys()){
			Channel channel= sk.channel();
			if (channel instanceof SocketChannel && channel != owner) {
				SocketChannel sChannel =(SocketChannel) channel;
			
				try {
					sChannel.write(getBuffer(msg));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	
	//在线人数，也就是socketchannel的数量
	public int OnlineNum(Selector selector) {
		return selector.keys().size()-1;
	}
	
	
	public static void main(String[] args) throws IOException 
    {
        new NIOServer(8888).listen();
    }
	
}














