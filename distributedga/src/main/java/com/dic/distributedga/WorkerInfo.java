package com.dic.distributedga;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

import com.dic.distributedga.core.abstractga.BasePopulation;

public class WorkerInfo {

	private String ipAddress;
	private int portNo;
	private BasePopulation poplulation;
	private BasePopulation migrantPopulation;
	private Socket socket;
	private ObjectOutputStream sendStream;
	private WorkerSocketThread workerSocketThread;
	
	
	public WorkerInfo(String ipAddress, int portNo) {
		this.ipAddress = ipAddress;
		this.portNo = portNo;
	}
	/**
	 * @return the ipAddress
	 */
	public String getIpAddress() {
		return ipAddress;
	}
	/**
	 * @param ipAddress the ipAddress to set
	 */
	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}
	/**
	 * @return the portNo
	 */
	public int getPortNo() {
		return portNo;
	}
	/**
	 * @param portNo the portNo to set
	 */
	public void setPortNo(int portNo) {
		this.portNo = portNo;
	}
	/**
	 * @return the poplulation
	 */
	public BasePopulation getPoplulation() {
		return poplulation;
	}
	/**
	 * @param poplulation the poplulation to set
	 */
	public void setPoplulation(BasePopulation poplulation) {
		this.poplulation = poplulation;
	}

	/**
	 * @return the socket
	 */
	public Socket getSocket() {
		return socket;
	}
	/**
	 * @param socket the socket to set
	 * @throws IOException 
	 */
	public void setSocket(Socket socket) throws IOException {
		this.socket = socket;
		sendStream = new ObjectOutputStream(socket.getOutputStream());
	}
	
	/**
	 * @return the workerSocketThread
	 */
	public WorkerSocketThread getWorkerSocketThread() {
		return workerSocketThread;
	}
	/**
	 * @param workerSocketThread the workerSocketThread to set
	 */
	public void setWorkerSocketThread(WorkerSocketThread workerSocketThread) {
		this.workerSocketThread = workerSocketThread;
	}
	
	/**
	 * @return the sendStream
	 */
	public ObjectOutputStream getSendStream() {
		return sendStream;
	}
	/**
	 * @return the migrantPopulation
	 */
	public BasePopulation getMigrantPopulation() {
		return migrantPopulation;
	}
	
	/**
	 * @param migrantPopulation the migrantPopulation to set
	 */
	public void setMigrantPopulation(BasePopulation migrantPopulation) {
		this.migrantPopulation = migrantPopulation;
	}
}
