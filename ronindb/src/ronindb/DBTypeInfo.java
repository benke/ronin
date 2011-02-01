package ronindb;

import gw.config.CommonServices;
import gw.lang.reflect.BaseTypeInfo;
import gw.lang.reflect.ConstructorInfoBuilder;
import gw.lang.reflect.IConstructorHandler;
import gw.lang.reflect.IConstructorInfo;
import gw.lang.reflect.IMethodCallHandler;
import gw.lang.reflect.IMethodInfo;
import gw.lang.reflect.IPropertyAccessor;
import gw.lang.reflect.IPropertyInfo;
import gw.lang.reflect.IType;
import gw.lang.reflect.MethodInfoBuilder;
import gw.lang.reflect.ParameterInfoBuilder;
import gw.lang.reflect.PropertyInfoBuilder;
import gw.lang.reflect.TypeSystem;
import gw.lang.reflect.features.PropertyReference;
import gw.lang.reflect.java.IJavaType;
import gw.util.GosuStringUtil;
import gw.util.concurrent.LazyVar;
import org.slf4j.LoggerFactory;
import org.slf4j.profiler.Profiler;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class DBTypeInfo extends BaseTypeInfo {

  private Map<String, IPropertyInfo> _properties;
  private List<IMethodInfo> _methods;
  private LazyVar<Map<String, IPropertyInfo>> _arrayProperties = new LazyVar<Map<String,IPropertyInfo>>() {
    @Override
    protected Map<String, IPropertyInfo> init() {
      return makeArrayProperties();
    }
  };
  private LazyVar<Map<String, IMethodInfo>> _joinArrayMethods = new LazyVar<Map<String,IMethodInfo>>() {
    @Override
    protected Map<String, IMethodInfo> init() {
      return makeJoinArrayMethods();
    }
  };
  
  private IMethodInfo _getMethod;
  private IMethodInfo _idMethod;
  private IMethodInfo _updateMethod;
  private IMethodInfo _deleteMethod;
  private IMethodInfo _countMethod;
  private IMethodInfo _countWithSqlMethod;
  private IMethodInfo _findMethod;
  private IMethodInfo _findSortedMethod;
  private IMethodInfo _findPagedMethod;
  private IMethodInfo _findSortedPagedMethod;
  private IMethodInfo _findWithSqlMethod;
  private IPropertyInfo _newProperty;
  private IConstructorInfo _ctor;
  
  public DBTypeInfo(DBType dbType) {
    super(dbType);
    
    _getMethod = new MethodInfoBuilder().withName("fromID").withStatic()
      .withParameters(new ParameterInfoBuilder().withName("id").withType(IJavaType.pLONG))
      .withReturnType(dbType)
      .withCallHandler(new IMethodCallHandler() {
        @Override
        public Object handleCall(Object ctx, Object... args) {
          try {
            return selectById(getOwnersType().getName() + ".fromID()", args[0]);
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
        }
      }).build(this);
    _idMethod = new MethodInfoBuilder().withName("toID")
      .withReturnType(IJavaType.pLONG)
      .withCallHandler(new IMethodCallHandler() {
        @Override
        public Object handleCall(Object ctx, Object... args) {
          return ((CachedDBObject)ctx).getColumns().get("id");
        }
      }).build(this);
    _updateMethod = new MethodInfoBuilder().withName("update")
      .withCallHandler(new IMethodCallHandler() {
        @Override
        public Object handleCall(Object ctx, Object... args) {
          try {
            ((CachedDBObject)ctx).update();
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
          return null;
        }
      }).build(this);
    _deleteMethod = new MethodInfoBuilder().withName("delete")
      .withCallHandler(new IMethodCallHandler() {
        @Override
        public Object handleCall(Object ctx, Object... args) {
          try {
            ((CachedDBObject)ctx).delete();
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
          return null;
        }
      }).build(this);
    _countWithSqlMethod = new MethodInfoBuilder().withName("countWithSql").withStatic()
      .withParameters(new ParameterInfoBuilder().withName("sql").withType(IJavaType.STRING))
      .withReturnType(IJavaType.pINT)
      .withCallHandler(new IMethodCallHandler() {
        @Override
        public Object handleCall(Object ctx, Object... args) {
          try {
            return countFromSql(getOwnersType().getName() + ".countWithSql()", (String)args[0]);
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
        }
      }).build(this);
    _countMethod = new MethodInfoBuilder().withName("count").withStatic()
      .withParameters(new ParameterInfoBuilder().withName("template").withType(dbType))
      .withReturnType(IJavaType.pINT)
      .withCallHandler(new IMethodCallHandler() {
        @Override
        public Object handleCall(Object ctx, Object... args) {
          try {
            return countFromTemplate(getOwnersType().getName() + ".count()", (CachedDBObject)args[0]);
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
        }
      }).build(this);
    _findWithSqlMethod = new MethodInfoBuilder().withName("findWithSql").withStatic()
      .withParameters(new ParameterInfoBuilder().withName("sql").withType(IJavaType.STRING))
      .withReturnType(IJavaType.LIST.getGenericType().getParameterizedType(dbType))
      .withCallHandler(new IMethodCallHandler() {
        @Override
        public Object handleCall(Object ctx, Object... args) {
          try {
            return findFromSql(getOwnersType().getName() + ".findWithSql()", (String)args[0]);
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
        }
      }).build(this);
    _findMethod = new MethodInfoBuilder().withName("find").withStatic()
      .withParameters(new ParameterInfoBuilder().withName("template").withType(dbType))
      .withReturnType(IJavaType.LIST.getGenericType().getParameterizedType(dbType))
      .withCallHandler(new IMethodCallHandler() {
        @Override
        public Object handleCall(Object ctx, Object... args) {
          try {
            return findFromTemplate(getOwnersType().getName() + ".find()",
              (CachedDBObject)args[0], null, false, -1, -1);
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
        }
      }).build(this);
    _findSortedMethod = new MethodInfoBuilder().withName("findSorted").withStatic()
      .withParameters(new ParameterInfoBuilder().withName("template").withType(dbType),
          new ParameterInfoBuilder().withName("sortProperty").withType(TypeSystem.get(PropertyReference.class).getParameterizedType(dbType, IJavaType.OBJECT)),
          new ParameterInfoBuilder().withName("ascending").withType(IJavaType.pBOOLEAN))
      .withReturnType(IJavaType.LIST.getGenericType().getParameterizedType(dbType))
      .withCallHandler(new IMethodCallHandler() {
        @Override
        public Object handleCall(Object ctx, Object... args) {
          try {
            return findFromTemplate(getOwnersType().getName() + ".findSorted()", (CachedDBObject)args[0], (PropertyReference)args[1], (Boolean)args[2], -1, -1);
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
        }
      }).build(this);
    _findPagedMethod = new MethodInfoBuilder().withName("findPaged").withStatic()
      .withParameters(new ParameterInfoBuilder().withName("template").withType(dbType),
          new ParameterInfoBuilder().withName("pageSize").withType(IJavaType.pINT),
          new ParameterInfoBuilder().withName("offset").withType(IJavaType.pINT))
      .withReturnType(IJavaType.LIST.getGenericType().getParameterizedType(dbType))
      .withCallHandler(new IMethodCallHandler() {
        @Override
        public Object handleCall(Object ctx, Object... args) {
          try {
            return findFromTemplate(getOwnersType().getName() + ".findPaged()", (CachedDBObject)args[0], null, false, (Integer)args[1], (Integer)args[2]);
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
        }
      }).build(this);
    _findSortedPagedMethod = new MethodInfoBuilder().withName("findSortedPaged").withStatic()
      .withParameters(new ParameterInfoBuilder().withName("template").withType(dbType),
          new ParameterInfoBuilder().withName("sortProperty").withType(TypeSystem.get(PropertyReference.class).getParameterizedType(dbType, IJavaType.OBJECT)),
          new ParameterInfoBuilder().withName("ascending").withType(IJavaType.pBOOLEAN),
          new ParameterInfoBuilder().withName("pageSize").withType(IJavaType.pINT),
          new ParameterInfoBuilder().withName("offset").withType(IJavaType.pINT))
      .withReturnType(IJavaType.LIST.getGenericType().getParameterizedType(dbType))
      .withCallHandler(new IMethodCallHandler() {
        @Override
        public Object handleCall(Object ctx, Object... args) {
          try {
            return findFromTemplate(getOwnersType().getName() + ".findSortedPaged()", (CachedDBObject)args[0], (PropertyReference)args[1], (Boolean)args[2], (Integer)args[3], (Integer)args[4]);
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
        }
      }).build(this);

    _newProperty = new PropertyInfoBuilder().withName("_New").withType(IJavaType.pBOOLEAN)
      .withWritable(false).withAccessor(new IPropertyAccessor() {
        @Override
        public void setValue(Object ctx, Object value) {
        }
        @Override
        public Object getValue(Object ctx) {
          return ((CachedDBObject)ctx).isNew();
        }
      }).build(this);
    _properties = new HashMap<String, IPropertyInfo>();
    try {
      Connection conn = connect();
      try {
        ResultSet cols = conn.getMetaData().getColumns(null, null, dbType.getRelativeName(), null);
        try {
          cols.first();
          while(!cols.isAfterLast()) {
            String col = cols.getString("COLUMN_NAME");
            int colType = cols.getInt("DATA_TYPE");
            IPropertyInfo prop = makeProperty(col, colType);
            _properties.put(prop.getName(), prop);
            cols.next();
          }
        } finally {
          cols.close();
        }
      } finally {
        conn.close();
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
    
    _ctor = new ConstructorInfoBuilder()
      .withConstructorHandler(new IConstructorHandler() {
        @Override
        public Object newInstance(Object... args) {
          return create();
        }
      }).build(this);
    
    _methods = new ArrayList<IMethodInfo>(Arrays.asList(_getMethod, _idMethod, _updateMethod, _deleteMethod, _countWithSqlMethod,
        _countMethod, _findWithSqlMethod, _findMethod, _findSortedMethod, _findPagedMethod,
        _findSortedPagedMethod));
    
    CommonServices.getEntityAccess().addEnhancementMethods(dbType, _methods);
    CommonServices.getEntityAccess().addEnhancementProperties(dbType, _properties, true);
  }

  @Override
  public List<? extends IPropertyInfo> getProperties() {
    List<IPropertyInfo> props = new ArrayList<IPropertyInfo>(_properties.values());
    props.addAll(_arrayProperties.get().values());
    props.add(_newProperty);
    return props;
  }

  @Override
  public IPropertyInfo getProperty(CharSequence propName) {
    if(propName.equals("_New")) {
      return _newProperty;
    }
    IPropertyInfo prop = _properties.get(propName.toString());
    if(prop == null) {
      prop = _arrayProperties.get().get(propName.toString());
    }
    return prop;
  }

  @Override
  public CharSequence getRealPropertyName(CharSequence propName) {
    for(String key : _properties.keySet()) {
      if(key.equalsIgnoreCase(propName.toString())) {
        return key;
      }
    }
    for(String key : _arrayProperties.get().keySet()) {
      if(key.equalsIgnoreCase(propName.toString())) {
        return key;
      }
    }
    return propName;
  }

  @Override
  public List<? extends IMethodInfo> getMethods() {
    return _methods;
  }

  @Override
  public IMethodInfo getMethod(CharSequence methodName, IType... params) {
    if("fromID".equals(methodName) && params != null && params.length == 1 && params[0].equals(IJavaType.pLONG)) {
      return _getMethod;
    }
    if("toID".equals(methodName) && (params == null || params.length == 0)) {
      return _idMethod;
    }
    if("update".equals(methodName) && (params == null || params.length == 0)) {
      return _updateMethod;
    }
    if("delete".equals(methodName) && (params == null || params.length == 0)) {
      return _deleteMethod;
    }
    if("findWithSql".equals(methodName) && params != null && params.length == 1 && params[0].equals(IJavaType.STRING)) {
      return _findWithSqlMethod;
    }
    if("find".equals(methodName) && params != null && params.length == 1 && params[0].equals(getOwnersType())) {
      return _findMethod;
    }
    if("findSorted".equals(methodName) && params != null && params.length == 3 && params[0].equals(getOwnersType()) && TypeSystem.get(PropertyReference.class).isAssignableFrom(params[1]) && params[2].equals(IJavaType.pBOOLEAN)) {
      return _findSortedMethod;
    }
    if("findPaged".equals(methodName) && params != null && params.length == 3 && params[0].equals(getOwnersType()) && params[1].equals(IJavaType.pINT) && params[2].equals(IJavaType.pINT)) {
      return _findPagedMethod;
    }
    if("findSortedPaged".equals(methodName) && params != null && params.length == 5 && params[0].equals(getOwnersType()) && TypeSystem.get(PropertyReference.class).isAssignableFrom(params[1]) && params[2].equals(IJavaType.pBOOLEAN) && params[3].equals(IJavaType.pINT) && params[4].equals(IJavaType.pINT)) {
      return _findSortedPagedMethod;
    }
    if("count".equals(methodName) && params != null && params.length == 1 && params[0].equals(getOwnersType())) {
      return _countMethod;
    }
    if("countWithSql".equals(methodName) && params != null && params.length == 1 && params[0].equals(IJavaType.STRING)) {
      return _countWithSqlMethod;
    }
    return null;
  }

  @Override
  public IMethodInfo getCallableMethod(CharSequence method, IType... params) {
    return getMethod(method, params);
  }

  @Override
  public List<? extends IConstructorInfo> getConstructors() {
    return Collections.singletonList(_ctor);
  }

  @Override
  public IConstructorInfo getConstructor(IType... params) {
    if(params == null || params.length == 0) {
      return _ctor;
    }
    return null;
  }

  @Override
  public IConstructorInfo getCallableConstructor(IType... params) {
    return getConstructor(params);
  }
  
  private Connection connect() throws SQLException {
    return getOwnersType().getConnection().connect();
  }
  
  CachedDBObject selectById(String feature, Object id) throws SQLException {
    CachedDBObject obj = null;
    String query = "select * from \"" + getOwnersType().getRelativeName() + "\" where \"id\" = '" + id.toString().replace("'", "''") + "'";
    Profiler profiler = Util.newProfiler(feature);
    profiler.start(query);
    Connection conn = connect();
    try {
      Statement stmt = conn.createStatement();
      try {
        stmt.executeQuery(query);
        ResultSet result = stmt.getResultSet();
        try {
          if(result.first()) {
            obj = buildObject(result);
          }
        } finally {
          result.close();
        }
      } finally {
        stmt.close();
      }
    } finally {
      conn.close();
      profiler.stop();
    }
    return obj;
  }

  List<CachedDBObject> findFromTemplate(String featureName, CachedDBObject template, PropertyReference sortColumn, boolean ascending, int limit, int offset) throws SQLException {
    StringBuilder query = new StringBuilder("select * from \"");
    query.append(getOwnersType().getRelativeName()).append("\" where ");
    addWhereClause(query, template);
    if(sortColumn != null) {
      query.append(" order by \"").append(sortColumn.getPropertyInfo().getName()).append("\" ").append(ascending ? "ASC" : "DESC").append(", \"id\" ASC");
    } else {
      query.append(" order by \"id\" ASC");
    }
    if(limit != -1) {
      query.append(" limit ").append(limit).append(" offset ").append(offset);
    }
    return findFromSql(featureName, query.toString());
  }
  
  int countFromTemplate(String feature, CachedDBObject template) throws SQLException {
    StringBuilder query = new StringBuilder("select count(*) as count from \"").append(getOwnersType().getRelativeName()).append("\" where ");
    addWhereClause(query, template);
    return countFromSql(feature, query.toString());
  }
  
  private int countFromSql(String feature, String query) throws SQLException {
    Profiler profiler = Util.newProfiler(feature);
    profiler.start(query);
    Connection conn = connect();
    try {
      Statement stmt = conn.createStatement();
      try {
        stmt.executeQuery(query);
        ResultSet result = stmt.getResultSet();
        try {
          if(result.first()) {
            return result.getInt("count");
          } else {
            return 0;
          }
        } finally {
          result.close();
        }
      } finally {
        stmt.close();
      }
    } finally {
      conn.close();
      profiler.stop();
    }
  }
  
  private void addWhereClause(StringBuilder query, CachedDBObject template) {
    List<String> whereClause = new ArrayList<String>();
    if(template != null) {
      for(Entry<String, Object> column : template.getColumns().entrySet()) {
        if(column.getValue() != null) {
          String value = "'" + column.getValue().toString().replace("'", "''") + "'";
          whereClause.add("\"" + column.getKey() + "\" = " + value);
        }
      }
      if(!whereClause.isEmpty()) {
        query.append(GosuStringUtil.join(whereClause, " and "));
      } else {
        query.append("true");
      }
    } else {
      query.append("true");
    }
  }
  
  List<CachedDBObject> findInDb(String featureName, List<IPropertyInfo> props, Object... args) throws SQLException {
    List<String> whereClause = new ArrayList<String>();
    for(int i = 0; i < props.size(); i++) {
      IPropertyInfo p = props.get(i);
      if(p instanceof DBPropertyInfo) {
        DBPropertyInfo dbProperty = (DBPropertyInfo) p;
        String value;
        if(dbProperty.getColumnName().endsWith("_id")) {
          value = ((CachedDBObject)args[i]).getColumns().get("id").toString();
        } else {
          value = "'" + args[i].toString().replace("'", "''") + "'";
        }
        whereClause.add("\"" + dbProperty.getColumnName() + "\" = " + value);
      }
    }
    return findFromSql(featureName, "select * from \"" + getOwnersType().getRelativeName() + "\" where " + GosuStringUtil.join(whereClause, " and "));
  }
  
  List<CachedDBObject> findFromSql(String feature, String query) throws SQLException {
    List<CachedDBObject> objs = new ArrayList<CachedDBObject>();
    Profiler profiler = Util.newProfiler(feature);
    profiler.start(query);
    Connection conn = connect();
    try {
      Statement stmt = conn.createStatement();
      try {
        stmt.executeQuery(query);
        ResultSet result = stmt.getResultSet();
        try {
          if(result.first()) {
            objs = buildObjects(result);
          }
        } finally {
          result.close();
        }
      } finally {
        stmt.close();
      }
    } finally {
      conn.close();
      profiler.stop();
    }
    return Collections.unmodifiableList(objs);
  }

  private ArrayList<CachedDBObject> buildObjects(ResultSet result) throws SQLException {
    ArrayList<CachedDBObject> objs = new ArrayList<CachedDBObject>();
    while(!result.isAfterLast()) {
      objs.add(buildObject(result));
      result.next();
    }
    return objs;
  }
  
  private CachedDBObject buildObject(ResultSet result) throws SQLException {
    CachedDBObject obj = new CachedDBObject(getOwnersType().getRelativeName(), (DBTypeLoader) getOwnersType().getTypeLoader(), getOwnersType().getConnection(), false);
    for(IPropertyInfo prop : getProperties()) {
      if(prop instanceof DBPropertyInfo) {
        DBPropertyInfo dbProp = (DBPropertyInfo) prop;
        Object resultObject = result.getObject(getOwnersType().getRelativeName() + "." + dbProp.getColumnName());
        if(resultObject instanceof BufferedReader) {
          obj.getColumns().put(dbProp.getColumnName(), readAll((BufferedReader)resultObject));
        } else if(resultObject instanceof Clob) {
          obj.getColumns().put(dbProp.getColumnName(), readAll(new BufferedReader(((Clob) resultObject).getCharacterStream())));
        } else if (dbProp.getFeatureType().equals(IJavaType.pBOOLEAN) && resultObject == null) {
          obj.getColumns().put(dbProp.getColumnName(), Boolean.FALSE);
        } else {
          obj.getColumns().put(dbProp.getColumnName(), resultObject);
        }
      }
    }
    return obj;
  }
  
  private Object readAll(BufferedReader r) {
    try {
      StringBuilder b = new StringBuilder();
      String line = r.readLine();
      while(line != null) {
        b.append(line).append("\n");
        line = r.readLine();
      }
      if(b.length() > 0) {
        b.setLength(b.length() - 1);
      }
      return b.toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  CachedDBObject create() {
    return new CachedDBObject(getOwnersType().getRelativeName(), (DBTypeLoader)getOwnersType().getTypeLoader(), getOwnersType().getConnection(), true);
  }
  
  private Map<String, IPropertyInfo> makeArrayProperties() {
    Map<String, IPropertyInfo> arrayProps = new HashMap<String, IPropertyInfo>();
    for(String fkTable : getOwnersType().getConnection().getFKs(getOwnersType().getRelativeName())) {
      IPropertyInfo arrayProp = makeArrayProperty(fkTable);
      arrayProps.put(arrayProp.getName(), arrayProp);
    }
    for(Join joinTable : getOwnersType().getConnection().getJoins(getOwnersType().getRelativeName())) {
      IPropertyInfo joinProp = makeJoinProperty(joinTable);
      arrayProps.put(joinProp.getName(), joinProp);
    }
    return arrayProps;
  }
  
  private Map<String, IMethodInfo> makeJoinArrayMethods() {
    return new HashMap<String, IMethodInfo>();
  }
  
  private DBPropertyInfo makeProperty(String propName, int type) {
    return new DBPropertyInfo(this, propName, type);
  }
  
  private IPropertyInfo makeArrayProperty(final String fkTable) {
    String namespace = getOwnersType().getConnection().getNamespace();
    final IType fkType = getOwnersType().getTypeLoader().getType(namespace + "." + fkTable);
    return new PropertyInfoBuilder().withName(fkTable + "s").withType(IJavaType.LIST.getGenericType().getParameterizedType(fkType))
      .withWritable(false).withAccessor(new IPropertyAccessor() {
        @Override
        public void setValue(Object ctx, Object value) {
        }
        @Override
        public Object getValue(Object ctx) {
          try {
            return ((DBTypeInfo) fkType.getTypeInfo()).findInDb(
              getOwnersType().getName() + "." + fkTable + "s",
              Arrays.asList(fkType.getTypeInfo().getProperty(getOwnersType().getRelativeName())), ctx);
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
        }
      }).build(this);
  }
  
  private IPropertyInfo makeJoinProperty(final Join join) {
    String namespace = getOwnersType().getConnection().getNamespace();
    final IType fkType = getOwnersType().getTypeLoader().getType(namespace + "." + join.getTargetTable());
    return new PropertyInfoBuilder().withName(join.getPropName()).withType(IJavaType.LIST.getGenericType().getParameterizedType(fkType))
      .withWritable(false).withAccessor(new IPropertyAccessor() {
        @Override
        public void setValue(Object ctx, Object value) {
        }
        @Override
        public Object getValue(Object ctx) {
          String j = join.getJoinTable();
          String t = join.getTargetTable();
          String o = getOwnersType().getRelativeName();
          if(GosuStringUtil.equals(t, o)) {
            o += "_src";
            t += "_dest";
          }
          String id = ((CachedDBObject)ctx).getColumns().get("id").toString();
          try {
            List<CachedDBObject> result = ((DBTypeInfo)fkType.getTypeInfo()).findFromSql(
                getOwnersType().getName() + "." + join.getPropName(),
                "select * from \"" + join.getTargetTable() + "\", \"" + j + "\" as j where j.\"" + t + "_id\" = \"" + join.getTargetTable() + "\".\"id\" and j.\"" + o + "_id\" = " + id
            );
            return new JoinResult(result, getOwnersType().getConnection(), j, o, t, id);
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
        }
      }).build(this);
  }

  @Override
  public DBType getOwnersType() {
    return (DBType) super.getOwnersType();
  }
  
  
}