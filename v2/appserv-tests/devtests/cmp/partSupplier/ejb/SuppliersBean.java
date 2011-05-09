
package Data;

import javax.ejb.*;

/**
 * Created Dec 16, 2002 1:22:07 PM
 * Code generated by the Forte For Java EJB Builder
 * @author mvatkina
 */


public abstract class SuppliersBean implements javax.ejb.EntityBean {
    
    private javax.ejb.EntityContext context;
    private LocalParts part0;
    
    
    /**
     * @see javax.ejb.EntityBean#setEntityContext(javax.ejb.EntityContext)
     */
    public void setEntityContext(javax.ejb.EntityContext aContext) {
        context=aContext;
    }
    
    
    /**
     * @see javax.ejb.EntityBean#ejbActivate()
     */
    public void ejbActivate() {
        
    }
    
    
    /**
     * @see javax.ejb.EntityBean#ejbPassivate()
     */
    public void ejbPassivate() {
        
    }
    
    
    /**
     * @see javax.ejb.EntityBean#ejbRemove()
     */
    public void ejbRemove() {
        Data.LocalParts part = getParts();
        System.out.print("Removing Supplier: " + getName());
        if (part == null) {
            System.out.println(" ...for cascade-delete");
        } else {
            System.out.println(" for Part: " + part.getName());
        }
        part0.testInCascadeDelete();
    }
    
    
    /**
     * @see javax.ejb.EntityBean#unsetEntityContext()
     */
    public void unsetEntityContext() {
        context=null;
    }
    
    
    /**
     * @see javax.ejb.EntityBean#ejbLoad()
     */
    public void ejbLoad() {
        
    }
    
    
    /**
     * @see javax.ejb.EntityBean#ejbStore()
     */
    public void ejbStore() {
        
    }
    
    public abstract java.lang.Integer getPartid();
    public abstract void setPartid(java.lang.Integer partid);
    
    public abstract java.lang.Integer getSupplierid();
    public abstract void setSupplierid(java.lang.Integer supplierid);
    
    public abstract java.lang.String getName();
    public abstract void setName(java.lang.String name);
    
    public abstract int getStatus();
    public abstract void setStatus(int status);
    
    public abstract java.lang.String getCity();
    public abstract void setCity(java.lang.String city);
    
    public abstract Data.LocalParts getParts();
    
    public abstract void setParts(Data.LocalParts parts);
    
    public Data.SuppliersKey ejbCreate(java.lang.Integer partid, java.lang.Integer supplierid, java.lang.String name, int status, java.lang.String city) throws javax.ejb.CreateException {
        if ((supplierid == null) ||(partid == null)){
            throw new javax.ejb.CreateException("The partid and supplierid are required.");
        }
        setPartid(partid);
        setSupplierid(supplierid);
        setName(name);
        setStatus(status);
        setCity(city); 
        return null;
    }
    
    public void ejbPostCreate(java.lang.Integer partid, java.lang.Integer supplierid, java.lang.String name, int status, java.lang.String city) throws javax.ejb.CreateException {
        part0 = getParts();
    }
    
}
