package jobs;

import javax.persistence.OptimisticLockException;

import models.MsgDb;
import play.db.jpa.GenericModel;
import play.db.jpa.JPABase;
import play.db.jpa.Model;
import play.jobs.Every;
import play.jobs.Job;

@Every("3s")
public class RoutineJob extends Job {
	@Override
	public void doJob() {
//		System.out.println("job started");
		while (true) {
			MsgDb obj = (MsgDb) SendingQueue.getInstance().getQueueHead();
			if (obj != null) {
				obj.setAge(obj.getAge() + 1);
				try {
					GenericModel merge = obj.merge();
					merge.save();
				} catch (OptimisticLockException e) {
					// someone else has changed the row in the middle...
					e.printStackTrace();
				}
			} else {
				break;
			}
		}
	}
}
