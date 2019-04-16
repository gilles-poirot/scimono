
package com.sap.scimono.callback.schemas;

import static com.sap.scimono.helper.Strings.isNullOrEmpty;
import static com.sap.scimono.helper.Strings.stripStart;

import java.util.*;
import java.util.regex.Pattern;

import com.sap.scimono.entity.Resource;
import com.sap.scimono.entity.schema.Attribute;
import com.sap.scimono.entity.schema.Schema;
import com.sap.scimono.entity.schema.resources.SchemaCSVReader;
import com.sap.scimono.helper.Strings;

public interface SchemasCallback {
  Pattern SCHEMA_PATTERN = Pattern.compile("^urn:[a-z0-9][a-z0-9-]{0,31}:([A-Za-z0-9()+,\\-.:=@;$_!*']|%[0-9a-f]{2})+$");
  String COMPLEX_ATTRIBUTE_DELIMETER = ".";
  String COMPLEX_ATTRIBUTE_DELIMETER_REGEX = "\\.";
  String SCHEMA_URN_DELIMETER = ":";
  String ATTRIBUTE_VALUE_FILTER_OPENING = "[";
  String ATTRIBUTE_VALUE_FILTER_CLOSING = "]";

  /**
   * Returns the schema with the specified schemaId.
   *
   * @param schemaId
   * @return
   */
  Schema getCustomSchema(final String schemaId);

  default Schema getSchema(final String schemaId) {
    if (isCustomSchema(schemaId)) {
      return getCustomSchema(schemaId);
    } else {
      Map<String, Schema> coreSchemas = SchemaCSVReader.getImportedSchemasFromCSVs();
      return coreSchemas.get(schemaId);
    }
  }

  /**
   * Creates a schema with the provided attributes. The schema object must have all mandatory attributes available,
   * including metadata (id, version, etc.).
   *
   * @param schema
   */
  void createCustomSchema(final Schema schema);

  /**
   * Returns a list of all schemas available in the system.
   * Must also return SCIM-defined core schemas (User, Group, Enterprise, etc.)
   *
   * @return list of schemas or empty list if none exist
   */
  List<Schema> getCustomSchemas();

  default List<Schema> getSchemas() {
    Map<String, Schema> coreSchemas = SchemaCSVReader.getImportedSchemasFromCSVs();

    List<Schema> schemas = new ArrayList<>();
    schemas.addAll(coreSchemas.values());
    schemas.addAll(getCustomSchemas());

    return schemas;
  }

  /**
   * Deletes the schema with the specified schemaId.
   *
   * @param schemaId
   */
  void deleteCustomSchema(final String schemaId);

  /**
   * Determines whether the provided schema name is a valid database identifier. If yes, the schema name is used as table name.
   *
   * @param schemaName
   * @return true if the schema name can be used as a table name
   */
  boolean isValidSchemaName(final String schemaName);

  Attribute getAttribute(String path);

  //TODO this could probably be optimized (e.g. it reads all schemas then returns only an id, which is used to read the schema again in getComplexAttributePath)
  default String getSchemaIdFromAttributeNotation(String attrNotation) {
    if (attrNotation.matches(SCHEMA_PATTERN.toString())) {
      // @formatter:off
      return getSchemas().stream()
          .map(Resource::getId)
          .filter(attrNotation::startsWith)
          .findAny()
          .get();
      // @formatter:on
    }
    return null;
  }

  //TODO needs javadoc
  default String removeSchemaFromAttributeNotation(final String attrNotation, final String schemaId) {
    String attrName = attrNotation.substring(attrNotation.indexOf(schemaId) + schemaId.length());
    return stripStart(attrName, SCHEMA_URN_DELIMETER);
  }

  //TODO needs javadoc
  default List<Attribute> getComplexAttributePath(final String fullAttrNotation) {
    String schemaId = getSchemaIdFromAttributeNotation(fullAttrNotation);
    String attrPath = removeSchemaFromAttributeNotation(fullAttrNotation, schemaId);
    List<Attribute> attrHierarchy = new LinkedList<>();

    if (isNullOrEmpty(attrPath)) {
      return attrHierarchy;
    }

    List<Attribute> subAttributes = getCustomSchema(schemaId).getAttributes();

    String[] attrPathSequence = attrPath.split(COMPLEX_ATTRIBUTE_DELIMETER_REGEX);
    for (String token : attrPathSequence) {

      // @formatter:off
      Optional<Attribute> attributeHolder = subAttributes.stream()
          .filter(attr -> token.equals(attr.getName()))
          .findAny();
      // @formatter:on

      if (!attributeHolder.isPresent()) {
        return new ArrayList<>();
      }

      Attribute currentAttr = attributeHolder.get();
      attrHierarchy.add(currentAttr);
      subAttributes = currentAttr.getSubAttributes();
    }

    return attrHierarchy;
  }

  static String addSchemaToPathIfNotExist(final String path, final String defaultSchema) {
    if (Strings.isNullOrEmpty(path) || path.matches(SCHEMA_PATTERN.toString())) {
      return path;
    }
    return String.join(SCHEMA_URN_DELIMETER, defaultSchema, path);
  }

  static boolean isAttributeNotationContainsSchema(final String fullAttrNotation) {
    return fullAttrNotation.matches(SCHEMA_PATTERN.toString());
  }

  default String appendSubAttributeToPath(final String fullAttributePath, final String subAttribute) {
    String combinedAttributePath;
    if (getCustomSchema(fullAttributePath) == null) {
      combinedAttributePath = String.join(COMPLEX_ATTRIBUTE_DELIMETER, fullAttributePath, subAttribute);
    } else {
      combinedAttributePath = String.join(SCHEMA_URN_DELIMETER, fullAttributePath, subAttribute);
    }

    return combinedAttributePath;
  }

  default boolean isAttributeNotationContainsValueFilter(final String fullAttributeNotation) {
    return fullAttributeNotation.contains(ATTRIBUTE_VALUE_FILTER_OPENING) && fullAttributeNotation.contains(ATTRIBUTE_VALUE_FILTER_CLOSING);
  }

  default String removeValueFilterFromAttributeNotation(final String fullAttributeNotation) {
    if (isAttributeNotationContainsValueFilter(fullAttributeNotation)) {
      return fullAttributeNotation.replaceAll("\\[.*\\]", "");
    }

    return fullAttributeNotation;
  }

  static boolean isCustomSchema(final String schemaId) {
    return schemaId.startsWith(Schema.EXTENSION_SCHEMA_URN);
  }
}