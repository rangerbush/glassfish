
package Data;

import javax.ejb.*;

/**
 * Created Dec 16, 2002 2:08:21 PM
 * Code generated by the Forte For Java EJB Builder
 * @author mvatkina
 */

public interface SPSessionHome extends javax.ejb.EJBHome {
    
    public Data.SPSession create()
    throws javax.ejb.CreateException, java.rmi.RemoteException;
    
}
