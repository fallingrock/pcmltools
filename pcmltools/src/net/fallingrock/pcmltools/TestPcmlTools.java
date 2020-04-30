package net.fallingrock.pcmltools;

import java.beans.PropertyVetoException;
import java.io.IOException;

import org.w3c.dom.Document;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400SecurityException;
import com.ibm.as400.access.CommandCall;
import com.ibm.as400.access.ErrorCompletingRequestException;
import com.ibm.as400.access.Trace;
import com.ibm.as400.data.ProgramCallDocument;

public class TestPcmlTools {

	public static void main(String[] args) {
		
		AS400 as400 = new AS400(HostInfo.HOSTNAME, HostInfo.USERID, HostInfo.PASSWORD);

		try {
			
//			CommandCall cc = new CommandCall(as400);
//			cc.run("CHGJOB LOG(4 00 *MSG) LOGCLPGM(*YES)");
//			
//			Trace.setTraceOn(true);
//			Trace.setTracePCMLOn(true);;
			
			PcmlTools pct = new PcmlTools(as400, "DGIBBS", "TEST1", PcmlTools.PGM);
			
			String xml = pct.getXML();
			Document doc = pct.getDocument();
			ProgramCallDocument pcml = pct.getPcml();
			
			pct.reset();
			
			xml = pct.getXML();
			doc = pct.getDocument();
			pcml = pct.getPcml();
			
			System.out.println();
			
		} catch (PcmlToolsException e) {
			e.printStackTrace();
		} finally {
			as400.disconnectAllServices();
		}
		
		
		
	}

}
