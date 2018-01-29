package org.fogbowcloud.blowout.core.model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.gson.Gson;

public class Specification implements Serializable {
	
	private static final String REQUIREMENTS_MAP_STR = "requirementsMap";
	private static final String USER_DATA_TYPE_STR = "userDataType";
	private static final String USER_DATA_FILE_STR = "userDataFile";
	private static final String CONTEXT_SCRIPT_STR = "contextScript";
	private static final String PRIVATE_KEY_FILE_PATH_STR = "privateKeyFilePath";
	private static final String PUBLIC_KEY_STR = "publicKey";
	private static final String USERNAME_STR = "username";
	private static final String IMAGE_STR = "image";

	private static final Logger LOGGER = Logger.getLogger(Specification.class);

	/**
	 * 
	 */
	private static final long serialVersionUID = 5255295548723927267L;
	
	String image;
	String username;
	String privateKeyFilePath;
	String publicKey;
	String contextScript;
	String userDataFile;
	String userDataType;
	
	Map<String, String> requirements = new HashMap<String, String>();

	public Specification(String image, String username, String publicKey, String privateKeyFilePath) {
		this(image, username, publicKey, privateKeyFilePath, "", "");
	}

	public Specification(String image, String username, String publicKey, String privateKeyFilePath,
			String userDataFile, String userDataType) {
		this.image = image;
		this.username = username;
		this.publicKey = publicKey;
		this.privateKeyFilePath = privateKeyFilePath;
		this.userDataFile = userDataFile;
		this.userDataType = userDataType;
	}

	public void addRequirement(String key, String value) {
		this.requirements.put(key, value);
	}

	public String getRequirementValue(String key) {
		return this.requirements.get(key);
	}

	public void putAllRequirements(Map<String, String> requirements) {
		for (Entry<String, String> e : requirements.entrySet()) {
			this.requirements.put(e.getKey(), e.getValue());
		}
	}

	public Map<String, String> getAllRequirements() {
		return this.requirements;
	}

	public void removeAllRequirements() {
		this.requirements = new HashMap<String, String>();
	}

	public static List<Specification> getSpecificationsFromJSonFile(String jsonFilePath)
			throws IOException {

		List<Specification> specifications = new ArrayList<Specification>();
		if (jsonFilePath != null && !jsonFilePath.trim().isEmpty()) {

			BufferedReader jsonFileStream = new BufferedReader(new FileReader(jsonFilePath));

			Gson gson = new Gson();
			specifications = Arrays.asList(gson.fromJson(jsonFileStream, Specification[].class));
			jsonFileStream.close();

			for (Specification specification : specifications) {

				File publicKeyFile = new File(specification.getPublicKey());
				if (publicKeyFile.exists()) {
					
					StringBuilder publicKey = new StringBuilder();
					BufferedReader publicKeyFileStream = new BufferedReader(
							new FileReader(publicKeyFile));

					String line;
					do {
						line = publicKeyFileStream.readLine();

						if (line != null) {
							publicKey.append(line);
						}

					} while (line != null && !line.trim().isEmpty());

					specification.setPublicKey(publicKey.toString());

					publicKeyFileStream.close();
				}
			}
		}
		return specifications;
	}

	public boolean parseToJsonFile(String jsonDestFilePath) {

		List<Specification> specification = new ArrayList<Specification>();
		specification.add(this);
		try {
			Specification.parseSpecsToJsonFile(specification, jsonDestFilePath);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static void parseSpecsToJsonFile(List<Specification> specs, String jsonDestFilePath) throws IOException {

		if (jsonDestFilePath != null && !jsonDestFilePath.trim().isEmpty()) {

			BufferedWriter jsonFileStream = null;
			try {	
				jsonFileStream = new BufferedWriter(new FileWriter(jsonDestFilePath));
				Gson gson = new Gson();
				String spectString = gson.toJson(specs);
				jsonFileStream.write(spectString);
				
			} catch (IOException e) {
				throw e;
			} finally {
				if (jsonFileStream != null) {
					jsonFileStream.close();
				}
			}	
		} else {
			throw new IllegalArgumentException("Invalid JSON file path");
		}
	}

	public String getImage() {
		return image;
	}

	public String getUsername() {
		return username;
	}

	public String getPrivateKeyFilePath() {
		return privateKeyFilePath;
	}

	public String getPublicKey() {
		return publicKey;
	}

	public void setPublicKey(String publicKey) {
		this.publicKey = publicKey;
	}

	public String getContextScript() {
		return contextScript;
	}

	public void setContextScript(String contextScript) {
		this.contextScript = contextScript;
	}

	public String getUserDataFile() {
		return userDataFile;
	}

	public void setUserDataFile(String userDataFile) {
		this.userDataFile = userDataFile;
	}

	public String getUserDataType() {
		return userDataType;
	}

	public void setUserDataType(String userDataType) {
		this.userDataType = userDataType;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Image: " + image);
		sb.append(" PublicKey: " + publicKey);
		if (contextScript != null && !contextScript.isEmpty()) {
			sb.append("\nContextScript: " + contextScript);
		}
		if (userDataFile != null && !userDataFile.isEmpty()) {
			sb.append("\nUserDataFile:" + userDataFile);
		}
		if (userDataType != null && !userDataType.isEmpty()) {
			sb.append("\nUserDataType:" + userDataType);
		}
		if (requirements != null && !requirements.isEmpty()) {
			sb.append("\nRequirements:{");
			for (Entry<String, String> entry : requirements.entrySet()) {
				sb.append("\n\t" + entry.getKey() + ": " + entry.getValue());
			}
			sb.append("\n}");
		}
		return sb.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((contextScript == null) ? 0 : contextScript.hashCode());
		result = prime * result + ((image == null) ? 0 : image.hashCode());
		result = prime * result + ((privateKeyFilePath == null) ? 0 : privateKeyFilePath.hashCode());
		result = prime * result + ((publicKey == null) ? 0 : publicKey.hashCode());
		result = prime * result + ((userDataFile == null) ? 0 : userDataFile.hashCode());
		result = prime * result + ((userDataType == null) ? 0 : userDataType.hashCode());
		result = prime * result + ((requirements == null) ? 0 : requirements.hashCode());
		result = prime * result + ((username == null) ? 0 : username.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Specification other = (Specification) obj;
		if (contextScript == null) {
			if (other.contextScript != null)
				return false;
		} else if (!contextScript.equals(other.contextScript))
			return false;
		if (image == null) {
			if (other.image != null)
				return false;
		} else if (!image.equals(other.image))
			return false;
		if (privateKeyFilePath == null) {
			if (other.privateKeyFilePath != null)
				return false;
		} else if (!privateKeyFilePath.equals(other.privateKeyFilePath))
			return false;
		if (publicKey == null) {
			if (other.publicKey != null)
				return false;
		} else if (!publicKey.equals(other.publicKey))
			return false;
		if (userDataFile == null) {
			if (other.userDataFile != null)
				return false;
		} else if (!userDataFile.equals(other.userDataFile))
			return false;
		if (userDataType == null) {
			if (other.userDataType != null)
				return false;
		} else if (!userDataType.equals(other.userDataType))
			return false;
		if (requirements == null) {
			if (other.requirements != null)
				return false;
		} else if (!requirements.equals(other.requirements))
			return false;
		if (username == null) {
			if (other.username != null)
				return false;
		} else if (!username.equals(other.username))
			return false;
		return true;
	}

	public Specification clone() {
		Specification cloneSpec = new Specification(this.image, this.username, this.publicKey, this.privateKeyFilePath,
				this.userDataFile, this.userDataType);
		cloneSpec.putAllRequirements(this.getAllRequirements());
		return cloneSpec;
	}

	public JSONObject toJSON() {
		try {
			JSONObject specification = new JSONObject();
			specification.put(IMAGE_STR, this.getImage());
			specification.put(USERNAME_STR, this.getUsername());
			specification.put(PUBLIC_KEY_STR, this.getPublicKey());
			specification.put(PRIVATE_KEY_FILE_PATH_STR, this.getPrivateKeyFilePath());
			specification.put(CONTEXT_SCRIPT_STR, this.getContextScript());
			specification.put(USER_DATA_FILE_STR, this.getUserDataFile());
			specification.put(USER_DATA_TYPE_STR, this.getUserDataType());
			specification.put(REQUIREMENTS_MAP_STR, getAllRequirements().toString());
			return specification;
		} catch (JSONException e) {
			LOGGER.debug("Error while trying to create a JSON from Specification", e);
			return null;
		}
	}

	public static Specification fromJSON(JSONObject specJSON) {
		Specification specification = new Specification(specJSON.optString(IMAGE_STR), specJSON.optString(USERNAME_STR),
				specJSON.optString(PUBLIC_KEY_STR), specJSON.optString(PRIVATE_KEY_FILE_PATH_STR),
				specJSON.optString(USER_DATA_FILE_STR), specJSON.optString(USER_DATA_TYPE_STR));
		HashMap<String, String> reqMap = (HashMap<String, String>) toMap(specJSON.optString(REQUIREMENTS_MAP_STR));
		specification.putAllRequirements(reqMap);
		return specification;
	}


	public static Map<String, String> toMap(String jsonStr) {
		Map<String, String> newMap = new HashMap<String, String>();
		jsonStr = jsonStr.replace("{", "").replace("}", "");
		String[] blocks = jsonStr.split(",");
		for (int i = 0; i < blocks.length; i++) {
			String block = blocks[i];
			int indexOfCarac = block.indexOf("=");
			if (indexOfCarac < 0) {
				continue;
			}
			String key = block.substring(0, indexOfCarac).trim();
			String value = block.substring(indexOfCarac + 1, block.length()).trim();
			newMap.put(key, value);
		}
		return newMap;
	}
}
