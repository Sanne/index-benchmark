package org.infinispan.lucene.profiling;

import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.ucieffe.model.Text;

/**
 * SharedState is used by LuceneUserThread when used concurrently to coordinate
 * the assertions: different threads need a shared state to know what to assert
 * about the Index contents.
 * 
 * @author Sanne Grinovero
 * @author Davide Di Somma
 * @since 4.0
 */
public class SharedState {

	final BlockingDeque<Text> textsInIndex = new LinkedBlockingDeque<Text>();
	final BlockingDeque<Text> textsOutOfIndex = new LinkedBlockingDeque<Text>();
	private final AtomicLong indexWriterActionCount = new AtomicLong();
	private final AtomicLong searchingActionCount = new AtomicLong();
	private final AtomicInteger errors = new AtomicInteger(0);
	private volatile boolean quit = false;
	private final CountDownLatch startSignal = new CountDownLatch(1);

	private int sizeObject;
	private final EntityManagerFactory entityManagerFactory;

	@SuppressWarnings("unchecked")
	public SharedState(int sizeObject, EntityManagerFactory entityManagerFactory) {
		this.entityManagerFactory = entityManagerFactory;
		if (sizeObject <= 0) {
			throw new RuntimeException(
					"sizeObject parameter must be greater than 0!");
		}

		this.sizeObject = sizeObject;

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try {
			
		List<Text> texts = entityManager.createQuery(
				"FROM Text ORDER BY oldId DESC").setMaxResults(this.sizeObject)
				.getResultList();

		textsOutOfIndex.addAll(texts);
		}
		finally {
			entityManager.close();
		}
	}

	public boolean needToQuit() {
		return quit || (errors.get() != 0);
	}

	public void errorManage(Exception e) {
		errors.incrementAndGet();
		e.printStackTrace();
	}

	public long incrementIndexWriterTaskCount(long delta) {
		return indexWriterActionCount.addAndGet(delta);
	}

	public long incrementIndexSearchesCount(long delta) {
		return searchingActionCount.addAndGet(delta);
	}

	public Text getTextToAddToIndex() throws InterruptedException {
		return textsOutOfIndex.take();
	}

	public int getSize() {
		return textsOutOfIndex.size();
	}

	public void quit() {
		quit = true;
	}

	public void addTextWrittenToIndex(Text termToAdd) {
		textsInIndex.add(termToAdd);
	}

	public void waitForStart() throws InterruptedException {
		startSignal.await();
	}

	public void startWaitingThreads() {
		startSignal.countDown();
	}

}
