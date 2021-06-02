


import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ElectionNode extends Remote {
	void passMessage(Message message) throws RemoteException, InterruptedException;
	void setLeft(ElectionNode newLeft)throws RemoteException, InterruptedException;
	void setRight(ElectionNode newRight)throws RemoteException, InterruptedException;

}
