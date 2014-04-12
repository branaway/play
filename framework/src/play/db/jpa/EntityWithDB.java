/**
 * 
 */
package play.db.jpa;

import javax.persistence.PersistenceUnit;

import play.db.DBConfig;

/**
 * @author bran
 *
 */
public class EntityWithDB {
	public String entity;
	public String persistenceUnit;

	public static EntityWithDB from(Class<? extends GenericModel> modelClass) {
		EntityWithDB en = new EntityWithDB();
		en.entity = modelClass.getSimpleName();
		PersistenceUnit pu = modelClass.getAnnotation(PersistenceUnit.class);
		if (pu == null)
			en.persistenceUnit = DBConfig.defaultDbConfigName;
		else {
			en.persistenceUnit = pu.name();
		}
		return en;
	}
}
