package org.fogbowcloud.blowout.infrastructure.provider.fogbow;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.fogbowcloud.blowout.infrastructure.model.FogbowResource;
import org.fogbowcloud.blowout.infrastructure.model.AbstractResource;

import condor.classad.AttrRef;
import condor.classad.ClassAdParser;
import condor.classad.Env;
import condor.classad.Expr;
import condor.classad.Op;
import condor.classad.RecordExpr;

public class FogbowRequirementsHelper {

	private static final Logger LOGGER = Logger.getLogger(FogbowRequirementsHelper.class);

	public static final String METADATA_FOGBOW_RESOURCE_KIND = "compute";
	public static final String METADATA_FOGBOW_REQUIREMENTS = "FogbowRequirements";
	public static final String METADATA_FOGBOW_REQUIREMENTS_Glue2vCPU = "Glue2vCPU";
	public static final String METADATA_FOGBOW_REQUIREMENTS_Glue2RAM = "Glue2RAM";
	public static final String METADATA_FOGBOW_REQUIREMENTS_Glue2disk = "Glue2disk";
	public static final String METADATA_FOGBOW_REQUIREMENTS_1Glue2CloudComputeManagerID = "Glue2CloudComputeManagerID";
	public static final String METADATA_FOGBOW_REQUEST_TYPE = "RequestType";

	public static boolean validateFogbowRequirementsSyntax(String requirementsString) {

		LOGGER.debug("Validating Fogbow Requirements [" + requirementsString + "]");

		if (requirementsString == null || requirementsString.trim().isEmpty()) {

			LOGGER.debug("Fogbow Requirements [" + requirementsString + "] Validate with sucess.");
			return true;
		}
		try {
			ClassAdParser adParser = new ClassAdParser(requirementsString);
			if (adParser.parse() != null) {

				LOGGER.debug(
						"Fogbow Requirements [" + requirementsString + "] Validate with sucess.");
				return true;
			} else {
				LOGGER.info("Fogbow Requirements [" + requirementsString
						+ "] Invalid - Expression not found.");
				return false;
			}
		} catch (Exception e) {
			LOGGER.error("Fogbow Requirements [" + requirementsString + "] Invalid", e);
			return false;
		}
	}

	public static boolean matches(FogbowResource resource, String requeriments) {

		LOGGER.debug("Matching Fogbow Requirements [" + requeriments + "] with Resource [id: "
				+ resource.getId() + "]");

		List<String> providedAttributes = new ArrayList<String>();
		List<String> foundedAttributes = new ArrayList<String>();

		try {
			if (requeriments == null || requeriments.trim().isEmpty()) {
				return true;
			}

			ClassAdParser classAdParser = new ClassAdParser(requeriments);
			Op expr = (Op) classAdParser.parse();

			providedAttributes.add(METADATA_FOGBOW_REQUIREMENTS_Glue2vCPU);
			providedAttributes.add(METADATA_FOGBOW_REQUIREMENTS_Glue2RAM);
			providedAttributes.add(METADATA_FOGBOW_REQUIREMENTS_Glue2disk);
			providedAttributes.add(METADATA_FOGBOW_REQUIREMENTS_1Glue2CloudComputeManagerID);

			Env env = new Env();
			for (String attribute : providedAttributes) {

				List<ValueAndOperator> findValuesInRequiremets = findValuesInRequiremets(expr,
						attribute);

				if (findValuesInRequiremets.size() > 0) {

					String attributeValue = null;

					if (attribute.equals(METADATA_FOGBOW_REQUIREMENTS_Glue2vCPU)) {
						
						foundedAttributes.add(attribute);
						attributeValue = resource.getMetadataValue(AbstractResource.METADATA_VCPU);
						
					} else if (attribute.equals(METADATA_FOGBOW_REQUIREMENTS_Glue2RAM)) {
						
						foundedAttributes.add(attribute);
						attributeValue = resource
								.getMetadataValue(AbstractResource.METADATA_MEN_SIZE);
						
					} else if (attribute.equals(METADATA_FOGBOW_REQUIREMENTS_Glue2disk)) {
						
						attributeValue = resource
								.getMetadataValue(AbstractResource.METADATA_DISK_SIZE);
						String zero = "0";
						if (attributeValue != null && !attributeValue.equals(zero)) {
							foundedAttributes.add(attribute);
						}
						
					} else if (attribute
							.equals(METADATA_FOGBOW_REQUIREMENTS_1Glue2CloudComputeManagerID)) {
						
						foundedAttributes.add(attribute);
						attributeValue = resource
								.getMetadataValue(AbstractResource.METADATA_LOCATION);
					}

					if (attributeValue != null) {
						env.push((RecordExpr) new ClassAdParser(
								"[" + attribute + " = " + attributeValue + "]").parse());
						LOGGER.debug("Matching Requirement [" + attribute + " = " + attributeValue
								+ "]");
					}
				}
			}

			if (foundedAttributes.isEmpty()) {
				return true;
			}
			expr = extractVariablesExpression(expr, foundedAttributes);

			return expr.eval(env).isTrue();

		} catch (Exception e) {
			LOGGER.error("Matching Fogbow Requirements [" + requeriments + "] with Resource [id: "
					+ resource.getId() + "] FAILED", e);
			return false;
		} finally {
			foundedAttributes.clear();
			providedAttributes.clear();
		}
	}

	private static Op extractVariablesExpression(Op expr, List<String> listAttName) {
		if (expr.arg1 instanceof AttrRef) {
			AttrRef attr = (AttrRef) expr.arg1;
			boolean thereIs = false;
			for (String attName : listAttName) {
				if (attr.name.rawString().equals(attName)) {
					thereIs = true;
				}
			}
			if (thereIs) {
				return expr;
			}
			return null;
		}
		Expr left = expr.arg1;
		if (left instanceof Op) {
			left = extractVariablesExpression((Op) expr.arg1, listAttName);
		}
		Expr right = expr.arg2;
		if (right instanceof Op) {
			right = extractVariablesExpression((Op) expr.arg2, listAttName);
		}
		try {
			if (left == null) {
				return (Op) right;
			} else if (right == null) {
				return (Op) left;
			}
		} catch (Exception e) {
			return null;
		}
		return new Op(expr.op, left, right);
	}

	private static List<ValueAndOperator> findValuesInRequiremets(Op expr, String attName) {
		List<ValueAndOperator> valuesAndOperator = new ArrayList<ValueAndOperator>();
		if (expr.arg1 instanceof AttrRef) {
			AttrRef attr = (AttrRef) expr.arg1;
			if (attr.name.rawString().equals(attName)) {
				valuesAndOperator.add(new ValueAndOperator(expr.arg2.toString(), expr.op));
			}
			return valuesAndOperator;
		}
		if (expr.arg1 instanceof Op) {
			List<ValueAndOperator> findValuesInRequiremets = findValuesInRequiremets((Op) expr.arg1,
					attName);
			if (findValuesInRequiremets != null) {
				valuesAndOperator.addAll(findValuesInRequiremets);
			}
		}
		if (expr.arg2 instanceof Op) {
			List<ValueAndOperator> findValuesInRequiremets = findValuesInRequiremets((Op) expr.arg2,
					attName);
			if (findValuesInRequiremets != null) {
				valuesAndOperator.addAll(findValuesInRequiremets);
			}
		}
		return valuesAndOperator;
	}

	protected static class ValueAndOperator {
		private String value;
		private int operator;

		public ValueAndOperator(String value, int operator) {
			this.value = value;
			this.operator = operator;
		}

		public int getOperator() {
			return operator;
		}

		public String getValue() {
			return value;
		}
	}

}
