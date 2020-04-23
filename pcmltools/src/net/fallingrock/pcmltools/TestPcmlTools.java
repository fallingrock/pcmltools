package net.fallingrock.pcmltools;

import com.ibm.as400.access.AS400;
import com.ibm.as400.data.ProgramCallDocument;

public class TestPcmlTools {

	public static void main(String[] args) {
		
		AS400 as400 = new AS400(HostInfo.HOSTNAME, HostInfo.USERID, HostInfo.PASSWORD);

		try {
			PcmlTools gp = new PcmlTools(as400);
			ProgramCallDocument doc = gp.loadFromObject("DGIBBS", "TEST1", "PGM");
			
			System.out.println();
			
		} catch (PcmlToolsException e) {
			e.printStackTrace();
		} finally {
			as400.disconnectAllServices();
		}
		
		
		
	}

}
