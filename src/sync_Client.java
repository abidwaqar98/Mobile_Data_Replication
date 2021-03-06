import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

// TODO: import GlobalConstants

public class sync_Client {

	public final static int BufferSize = 1024;

	private File rootDir;
	private String serverHostName;
	private int fileServerPort;
	private int metaDataServerPort;
	private String clientId;
	
	private String serverId;
	
	
	public sync_Client(String clientId, File rootDir, String serverHostName, int fileServerPort, int metaDataServerPort) {
		this.rootDir = rootDir;
		this.serverHostName = serverHostName;
		this.fileServerPort = fileServerPort;
		this.metaDataServerPort = metaDataServerPort;
		this.clientId = clientId;
	}
	
	private ArrayList<String> getMetaData() throws UnknownHostException, IOException {
		
		// Retrieve meta data from server.		
		Socket socket = new Socket(serverHostName, metaDataServerPort);
		PrintWriter printer = new PrintWriter(socket.getOutputStream());
		BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		
		//get Server ID
		serverId = reader.readLine();
		
		//sending clientID
		System.out.println("SC: ClientID:" + clientId);
		printer.println(clientId);
		printer.flush();
		
		// TODO: Retrieve meta data via reader object.
		
		//receiving metaData
		ArrayList<String> metaDataArr = new ArrayList<String>();		
		String metaData = reader.readLine();
		while (!metaData.equals("-1"))
		{
			metaDataArr.add(metaData);
			System.out.println(metaData);
			metaData = reader.readLine();
		}
		socket.close();
		return metaDataArr;
	}
	
	public void checkMetaFiles(ArrayList<String> metaDataArr) throws IOException
	{
		String myMetaData = new String(); 
		String[] serverMetaData;
		String[] myMetaDataArr;
		ArrayList<String>  filesToSync = new ArrayList<String>();
		int totalFiles = metaDataArr.size();
		System.out.println("MetaData: " + metaDataArr);
		
		
		for (int i = 0; i < totalFiles; ++i)
		{
			System.out.println(i);
			serverMetaData = metaDataArr.get(i).split(",");	
			System.out.println(rootDir.getCanonicalFile() + File.separator + serverMetaData[0] + global_Variables.MetaDataFileSuffix);
			File file_check = new File(rootDir.getCanonicalFile() + File.separator + serverMetaData[0] );
//			
			if (!file_check.exists())
			{
				filesToSync.add(metaDataArr.get(i));
				file_Creator.createMetaData(file_check);
			}
			else
			{
				try 
				{
					File file = new File(rootDir.getCanonicalFile() + File.separator + serverMetaData[0] +  global_Variables.MetaDataFileSuffix );
					BufferedReader reader = new BufferedReader(new FileReader(file));
					myMetaData = reader.readLine();
					reader.close();
				} 
				catch (IOException e)
				{
					e.printStackTrace();
				}
				myMetaDataArr = myMetaData.split(",");
				
				for (int j = 0; j < myMetaDataArr.length;  ++j)
					System.out.println(myMetaDataArr[j]);
				
				if (Long.parseLong(myMetaDataArr[myMetaDataArr.length-1]) < Long.parseLong(serverMetaData[serverMetaData.length-1]))
				{
					filesToSync.add(metaDataArr.get(i));
				}
				else if(Long.parseLong(myMetaDataArr[myMetaDataArr.length-1]) == Long.parseLong(serverMetaData[serverMetaData.length-1]) && Integer.parseInt(clientId) > Integer.parseInt(serverId))
				{
					filesToSync.add(metaDataArr.get(i));
					System.out.println("HOLA");
				}
			}
		}
		System.out.println("SC: Remaining metadata Files" + filesToSync);
		metaDataArr = filesToSync;
	}
	
	private void syncFiles(ArrayList<String> filesToSync) throws UnknownHostException, IOException {
		// TODO: Retrieve files to be updated from server (i.e., replace local replica) and change local meta data.
	
		Socket socket = new Socket(serverHostName, fileServerPort);
		PrintWriter printer = new PrintWriter(socket.getOutputStream());
		BufferedReader socket_reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		
		//sending client ID
		printer.println(clientId);
		printer.flush();
		
		//total files to sync
		printer.write(filesToSync.size());
		printer.flush();
		
		String fileName;
		String fileData;
		
		for (int i = 0; i< filesToSync.size(); ++i)
		{
			String[] metafile = filesToSync.get(i).split(",");
			fileName = metafile[0];		
			
			//sending file name to server
			printer.println(fileName);
			printer.flush();
			
			System.out.println(fileName);
			
			File file = new File(rootDir.getCanonicalFile() + File.separator + fileName);

			PrintWriter file_bw = new PrintWriter(new FileWriter(file));
			
			String socketData = socket_reader.readLine();
			while (!socketData.equals("-1")) {
				file_bw.println(socketData);
				
				socketData = socket_reader.readLine();
//				System.out.println(socketData);
			}
			file_bw.close();
			
			file = new File(rootDir.getCanonicalFile() + File.separator + fileName + global_Variables.MetaDataFileSuffix);
			global_Variables.resetModificationBit(file, serverId, metafile[1]);
		}
		printer.close();
		socket_reader.close();
		System.out.println("Files Synced");
	}

	public void sync() throws UnknownHostException, IOException {
		ArrayList<String> metaDataArr = getMetaData();
		checkMetaFiles(metaDataArr);
		syncFiles(metaDataArr);
	}
	
	private static void usage() {
		System.out.println("Usage:");
		System.out.println("FileSync CLIENT_ID ROOT_DIRECTORY SERVER_HOST_NAME FILE_SERVER_PORT META_DATA_SERVER_PORT");
	}
	
	public static void main(String[] args) {
		System.out.println("Total args Passed: " + args.length);
		
		if (args.length != 5) {
			usage();
			System.exit(-1);
		}
		
		String clientId = args[0];
		global_Variables.current_id = Integer.parseInt(clientId);
		
		String rootDirStr = args[1];
		File rootDir = new File(rootDirStr);
		if (!rootDir.isDirectory()) {
			System.err.println("Root directory '" + rootDir + "' is no directorty.");
			System.exit(-1);
		}
		if (!rootDir.exists()) {
			System.err.println("Root directory '" + rootDir + "' does not exist.");
			System.exit(-1);
		}
	
		String serverHostName = args[2];
		
		int fileServerPort = -1;
		try {
			fileServerPort = Integer.parseInt(args[3]);
		} catch (NumberFormatException e) {
			System.err.println("Invalid file server port.");
			System.exit(-1);
		}
		
		int metaDataServerPort = -1;
		try {
		 metaDataServerPort = Integer.parseInt(args[4]);
		} catch (NumberFormatException e) {
			System.err.println("Invalid meta data server port.");
			System.exit(-1);
		}
		
		sync_Client client = new sync_Client(clientId, rootDir, serverHostName, fileServerPort, metaDataServerPort);
		try {
			client.sync();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
