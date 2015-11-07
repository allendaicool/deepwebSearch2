package deepWebSearch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.codec.binary.Base64;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

class TreeNode
{
	private TreeNode parent;
	private List<TreeNode> children;
	private double esValue; //specifity
	private int ecValue; //coverage value
	private String nodeName;
	private String fileName;
	public TreeNode (TreeNode parent, String name, String filename)
	{
		this.parent = parent;
		children = new ArrayList<TreeNode>();
		this.nodeName = name;
		this.fileName = filename;
	}

	public String getFileName()
	{
		return this.fileName;
	}

	public void setEsValue(double esValue)
	{
		this.esValue = esValue;
	}

	public void setEcValue(int ecValue)
	{
		this.ecValue = ecValue;
	}

	public double getEsValue()
	{
		return this.esValue;
	}

	public int getEcValue()
	{
		return this.ecValue;
	}


	public TreeNode getParent()
	{
		return this.parent;
	}

	public List<TreeNode> getChildren()
	{
		return this.children;
	}

	public void addchildren(TreeNode child)
	{
		this.children.add(child);
	}

	public void setParent(TreeNode parent)
	{
		this.parent = parent;
	}

	public String getNodeName() {
		return nodeName;
	}
}

public class webSearch {

	TreeNode root = new TreeNode(null, "Root", rootFile);
	private static final String bingSearchURL = 
			"https://api.datamarket.azure.com/Data.ashx/Bing/SearchWeb/v1/Composite?Query=%27";
	private static final String bingSearchFomart = "%27&$top=4&$format=json";
	private static String accountKeyEnc;
	private static final String rootFile = "/root.txt";
	private static final String sportFile = "/sports.txt";
	private static final String healthFile = "/health.txt";
	private static final String computerFile = "/computers.txt";
	private String inputURL;
	private String site;
	private Map<String, TreeNode> treeNodeMapping = new HashMap<String, TreeNode>();
	private Map<String, Integer> queryCountMapping = new HashMap<String, Integer>();
	private Map<String, Set<String>> urlMapping = new HashMap<String, Set<String>>();
	private String curDir;
	private List<String> selectedCateogry = new ArrayList<String>();

	public webSearch(String accountKey)
	{
		byte[] accountKeyBytes = Base64.encodeBase64((accountKey + ":" + accountKey).getBytes());
		accountKeyEnc = new String(accountKeyBytes);
		root.setEsValue(1);
		queryCountMapping.put("Root", 0);
		treeNodeMapping.put("Root", root);
		curDir = System.getProperty("user.dir");
	}

	private void populateTree()
	{
		TreeNode computers = new TreeNode(root, "Computers", computerFile);
		TreeNode health = new TreeNode(root, "Health", healthFile);
		TreeNode sports = new TreeNode(root, "Sports", sportFile);
		root.addchildren(computers);
		root.addchildren(health);
		root.addchildren(sports);
		treeNodeMapping.put("Health", health);
		treeNodeMapping.put("Computers", computers);
		treeNodeMapping.put("Sports", sports);

		TreeNode disease = new TreeNode(health, "Diseases",null);
		TreeNode fitness = new TreeNode(health, "Fitness", null);
		treeNodeMapping.put("Diseases", disease);
		treeNodeMapping.put("Fitness", fitness);
		health.addchildren(disease);
		health.addchildren(fitness);
		TreeNode soccer = new TreeNode(sports, "Soccer", null);
		TreeNode basketball = new TreeNode(sports, "Basketball", null);
		treeNodeMapping.put("Soccer", soccer);
		treeNodeMapping.put("Basketball", basketball);
		sports.addchildren(soccer);
		sports.addchildren(basketball);
		TreeNode hardware = new TreeNode(computers, "Hardware", null);
		TreeNode programming = new TreeNode(computers, "Programming", null);
		treeNodeMapping.put("Hardware", hardware);
		treeNodeMapping.put("Programming", programming);
		computers.addchildren(hardware);
		computers.addchildren(programming);
		urlMapping.put("Root", new HashSet<String>());
		urlMapping.put("Computers", new HashSet<String>());
		urlMapping.put("Health", new HashSet<String>());
		urlMapping.put("Sports", new HashSet<String>());
	}

	private Map<String, List<String>> populateQueryMapping(TreeNode root)
	{
		Map<String, List<String>> queryMapping = new HashMap<String, List<String>>();
		BufferedReader br = null;
		try 
		{
			String sCurrentLine;
			br = new BufferedReader(new FileReader(curDir+root.getFileName()));
			while ((sCurrentLine = br.readLine()) != null) 
			{
				String[] afterProcessed = sCurrentLine.split("\\s+");
				if (!queryMapping.containsKey(afterProcessed[0]))
				{
					queryMapping.put(afterProcessed[0], new ArrayList<String>());
				}

				StringBuffer queryTerm = new StringBuffer();
				for (int i = 1; i < afterProcessed.length; i++)
				{
					queryTerm.append(afterProcessed[i]);
					if (i != afterProcessed.length - 1)
					{
						queryTerm.append("%20");
					}
				}
				queryMapping.get(afterProcessed[0]).add(queryTerm.toString());
			}
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		} 
		finally 
		{
			try 
			{
				if (br != null)br.close();
			} 
			catch (IOException ex) 
			{
				ex.printStackTrace();
			}
		}
		return queryMapping;
	}


	private String classifyDataBase(TreeNode root, double esValue, int ecValue, List<String> path)
	{
		String result = "";
		if (root.getChildren().isEmpty())
		{
			path.add(root.getNodeName());
			return "/"+ root.getNodeName();
		}
		Map<String, List<String>> queryMapping = populateQueryMapping(root);

		countWebTotal(queryMapping, root.getNodeName());
		calculateSpecificity(root);
		for (TreeNode child : root.getChildren())
		{
			if (child.getEcValue() >= ecValue && child.getEsValue() >= esValue)
			{
				path.add(root.getNodeName());
				result = result + "/" + root.getNodeName() + classifyDataBase(child, esValue, ecValue, path);
			}
		}
		if (result.equals(""))
		{
			path.add(root.getNodeName());
			return "/" + root.getNodeName();
		}
		return result;
	}

	private void calculateSpecificity (TreeNode root)
	{
		int sum = 0;
		for (TreeNode child : root.getChildren())
		{
			sum += child.getEcValue();
		}
		for (TreeNode child : root.getChildren())
		{
			double specifity = ((double)root.getEsValue()*child.getEcValue())/(double)sum; //
			child.setEsValue(specifity);
		}
	}

	private  void countWebTotal(Map<String, List<String>> queryMapping, String nodeName)
	{
		for (Map.Entry<String, List<String>> entry: queryMapping.entrySet())
		{
			String category = entry.getKey();
			List<String> quertTermList = entry.getValue();
			int numDocs = 0;
			for (String queryTerm : quertTermList)
			{
				numDocs += getWebTotalPerQuery(queryTerm, nodeName);
			}
			treeNodeMapping.get(category).setEcValue(numDocs);
		}
	}

	private int getWebTotalPerQuery(String queryTerm,String nodeName)
	{
		String URL = bingSearchURL + inputURL + queryTerm + bingSearchFomart;
		URL url;
		int totalDocs = 0;
		try 
		{
			url = new URL(URL);
			URLConnection urlConnection = url.openConnection();
			urlConnection.setRequestProperty("Authorization", "Basic " + accountKeyEnc);
			InputStream inputStream = (InputStream) urlConnection.getContent();		
			byte[] contentRaw = new byte[urlConnection.getContentLength()];
			inputStream.read(contentRaw);
			String content = new String(contentRaw);
			JSONObject obj= (JSONObject) JSONValue.parse(content);
			JSONObject obj2 = (JSONObject) obj.get("d");
			JSONArray obj3 = (JSONArray) obj2.get("results");
			JSONObject obj4 = (JSONObject) obj3.get(0);
			JSONArray array1 = (JSONArray)obj4.get("Web");
			if (array1.size() > 4)
			{
				System.out.println("it is wrong");
				System.exit(0);
			}
			for (int i = 0; i < array1.size(); i++)
			{
				JSONObject obj5 = (JSONObject) array1.get(i);
				String docURL = (String) obj5.get("Url");
				if (!urlMapping.get(nodeName).contains(docURL))
				{
					urlMapping.get(nodeName).add(docURL);
				}
			}
			totalDocs = Integer.valueOf((String)obj4.get("WebTotal"));
		}
		catch (MalformedURLException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		catch (IOException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return totalDocs;
	}

	private void outputToFile(String nodeName, TreeMap<String, Integer> wordCount)
	{
		File file = new File(nodeName + "-" + site + ".txt");
		try 
		{
			if (!file.exists())
			{
				file.createNewFile();
			}
			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			for (Map.Entry<String, Integer> entry : wordCount.entrySet())
			{
				bw.write(entry.getKey());
				bw.write("#");
				bw.write(String.valueOf(entry.getValue()));
				bw.write('\n');
			}
			bw.close();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	private void writeSummary(List<String> path)
	{
		Map<String, TreeMap<String, Integer>> wordCountMapping = new HashMap<String, TreeMap<String, Integer>>();
		for (int i = 0; i < path.size(); i++)
		{
			String pathNode = path.get(i);
			if (!urlMapping.containsKey(pathNode))
			{
				continue;
			}
			Set<String> urlSet = urlMapping.get(pathNode);
			System.out.println("url size is " + urlSet.size());
			wordCountMapping.put(pathNode, new TreeMap<String, Integer>());
			long time1 = System.currentTimeMillis();
			for (String tmpURL : urlSet)
			{
				long time3  =  System.currentTimeMillis();
				Set<String> wordSet = GetWordsLynx.runLynx(tmpURL);
				long time4  =  System.currentTimeMillis();
				System.out.println("time spent is " + (time4 -time3));
				for (int j = 0; j <= i; j++)
				{
					if (j < i && urlMapping.get(path.get(j)).contains(tmpURL))
					{
						continue;
					}
					if (j <i)
					{
						urlMapping.get(path.get(j)).add(tmpURL);
					}
					TreeMap<String, Integer> wordCount = wordCountMapping.get(path.get(j));
					for (String word :wordSet)
					{
						if (!wordCount.containsKey(word))
						{
							wordCount.put(word, 1);
						}
						else
						{
							wordCount.put(word, wordCount.get(word) + 1);
						}
					}
				}
				//				try
				//				{
				//					Thread.sleep(3000);
				//				} catch (InterruptedException e)
				//				{
				//					// TODO Auto-generated catch block
				//					e.printStackTrace();
				//				}
			}
			long time2 = System.currentTimeMillis();
			System.out.println("time used is " + (time2-time1));
		}
		for (String nodeName : path)
		{
			if (urlMapping.containsKey(nodeName))
				outputToFile(nodeName, wordCountMapping.get(nodeName));
		}
	}

	public static void main(String[] args)
	{
		//"hOVysMk4Ynb2GSI7COBxmjJf+GXpgKMP0xcy3RpYVY4";
        //VdX80WE14L8sXRuy7vjqMa/yB7Ocp8PTmHsm686MEE
		double esValue = Double.valueOf(args[1]);
		int ecValue = Integer.valueOf(args[2]);
		webSearch inst = new webSearch(args[0]);
		inst.site = args[3];
		inst.populateTree();
		inst.inputURL = "site%3a" + inst.site +"%20";
		System.out.println("url is " + inst.inputURL);
		List<String> path = new ArrayList<String>();
		String classification = inst.classifyDataBase(inst.root, esValue, ecValue, path);
		System.out.println("classification is " + classification);
		for (String path1 : path)
		{
			System.out.println(path1);
		}
		//		for (Map.Entry<String, Set<String>> urlEntry : inst.urlMapping.entrySet())
		//		{
		//			System.out.println("url key is " + urlEntry.getKey());
		//			System.out.println("url size is " + urlEntry.getValue().size());
		//		}
		inst.writeSummary(path);
	}

}
