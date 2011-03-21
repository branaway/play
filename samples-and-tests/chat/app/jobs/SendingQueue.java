package jobs;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import play.db.jpa.Model;

public class SendingQueue {
	private static SendingQueue instance = null;
	private static BlockingQueue<Model> queue;

	private SendingQueue() {
		queue = new LinkedBlockingQueue<Model>();
	}

	public static SendingQueue getInstance() {
		if (instance == null) {
			instance = new SendingQueue();
		}
		return instance == null ? new SendingQueue() : instance;
	}

	public int getQueueSize() {
		return queue.size();
	}

	public boolean queue(Model obj) {
		return queue.offer(obj);
	}

	public Model getQueueHead() {
		return queue.poll();
	}
}
