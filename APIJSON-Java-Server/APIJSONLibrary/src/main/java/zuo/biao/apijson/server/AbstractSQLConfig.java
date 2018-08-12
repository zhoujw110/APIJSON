/*Copyright ©2016 TommyLemon(https://github.com/TommyLemon/APIJSON)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/

package zuo.biao.apijson.server;

import static zuo.biao.apijson.JSONObject.KEY_COLUMN;
import static zuo.biao.apijson.JSONObject.KEY_COMBINE;
import static zuo.biao.apijson.JSONObject.KEY_GROUP;
import static zuo.biao.apijson.JSONObject.KEY_HAVING;
import static zuo.biao.apijson.JSONObject.KEY_ID;
import static zuo.biao.apijson.JSONObject.KEY_ID_IN;
import static zuo.biao.apijson.JSONObject.KEY_ORDER;
import static zuo.biao.apijson.JSONObject.KEY_ROLE;
import static zuo.biao.apijson.JSONObject.KEY_SCHEMA;
import static zuo.biao.apijson.JSONObject.KEY_USER_ID;
import static zuo.biao.apijson.JSONObject.KEY_USER_ID_IN;
import static zuo.biao.apijson.RequestMethod.DELETE;
import static zuo.biao.apijson.RequestMethod.GET;
import static zuo.biao.apijson.RequestMethod.GETS;
import static zuo.biao.apijson.RequestMethod.HEADS;
import static zuo.biao.apijson.RequestMethod.POST;
import static zuo.biao.apijson.RequestMethod.PUT;
import static zuo.biao.apijson.SQL.AND;
import static zuo.biao.apijson.SQL.NOT;
import static zuo.biao.apijson.SQL.OR;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;

import zuo.biao.apijson.Log;
import zuo.biao.apijson.RequestMethod;
import zuo.biao.apijson.RequestRole;
import zuo.biao.apijson.SQL;
import zuo.biao.apijson.StringUtil;
import zuo.biao.apijson.server.exception.NotExistException;
import zuo.biao.apijson.server.model.Column;
import zuo.biao.apijson.server.model.Table;

/**config sql for JSON Request
 * @author Lemon
 */
public abstract class AbstractSQLConfig implements SQLConfig {
	private static final String TAG = "SQLConfig";


	/**
	 * 表名映射，隐藏真实表名，对安全要求很高的表可以这么做
	 */
	public static final Map<String, String> TABLE_KEY_MAP;
	static {
		TABLE_KEY_MAP = new HashMap<String, String>();
		TABLE_KEY_MAP.put(Table.class.getSimpleName(), Table.TAG);
		TABLE_KEY_MAP.put(Column.class.getSimpleName(), Column.TAG);
	}



	private long id; //Table的id
	private RequestMethod method; //操作方法
	private boolean prepared = true; //预编译
	private boolean main = true;
	/**
	 * TODO 被关联的表通过就忽略关联的表？(这个不行 User:{"sex@":"/Comment/toId"})
	 */
	private RequestRole role; //发送请求的用户的角色
	private String schema; //表所在的数据库
	private String table; //表名
	private String alias; //表别名
	private String group; //分组方式的字符串数组，','分隔
	private String having; //聚合函数的字符串数组，','分隔
	private String order; //排序方式的字符串数组，','分隔
	private String column; //表内字段名(或函数名，仅查询操作可用)的字符串数组，','分隔
	private String values; //对应表内字段的值的字符串数组，','分隔
	private Map<String, Object> content; //Request内容，key:value形式，column = content.keySet()，values = content.values()
	private Map<String, Object> where; //筛选条件，key:value形式
	private Map<String, List<String>> combine; //条件组合，{ "&":[key], "|":[key], "!":[key] }


	//array item <<<<<<<<<<
	private int count; //Table数量
	private int page; //Table所在页码
	private int position; //Table在[]中的位置
	private int query; //JSONRequest.query
	private int type; //ObjectParser.type
	private List<Join> joinList; //joinList
	//array item >>>>>>>>>>
	private boolean test; //测试
	private boolean cacheStatic; //静态缓存

	public AbstractSQLConfig(RequestMethod method) {
		setMethod(method);
	}
	public AbstractSQLConfig(RequestMethod method, String table) {
		this(method);
		setTable(table);
	}
	public AbstractSQLConfig(RequestMethod method, int count, int page) {
		this(method);
		setCount(count);
		setPage(page);
	}

	@NotNull
	@Override
	public RequestMethod getMethod() {
		if (method == null) {
			method = GET;
		}
		return method;
	}
	@Override
	public AbstractSQLConfig setMethod(RequestMethod method) {
		this.method = method;
		return this;
	}
	@Override
	public boolean isPrepared() {
		return prepared;
	}
	@Override
	public AbstractSQLConfig setPrepared(boolean prepared) {
		this.prepared = prepared;
		return this;
	}
	@Override
	public boolean isMain() {
		return main;
	}
	@Override
	public AbstractSQLConfig setMain(boolean main) {
		this.main = main;
		return this;
	}


	@Override
	public long getId() {
		return id;
	}
	@Override
	public AbstractSQLConfig setId(long id) {
		this.id = id;
		return this;
	}

	@Override
	public RequestRole getRole() {
		//不能 @NotNull , AbstractParser#getSQLObject 内当getRole() == null时填充默认值
		return role;
	}
	public AbstractSQLConfig setRole(String roleName) throws Exception {
		return setRole(RequestRole.get(roleName));
	}
	@Override
	public AbstractSQLConfig setRole(RequestRole role) {
		this.role = role;
		return this;
	}

	@Override
	public String getSchema() {
		String sqlTable = getSQLTable();
		if (sqlTable != null && sqlTable.startsWith("`")) {
			return SCHEMA_INFORMATION;
		}
		return schema;
	}
	@Override
	public AbstractSQLConfig setSchema(String schema) {
		if (schema != null) {
			String s = schema.startsWith("`") && schema.endsWith("`") ? schema.substring(1, schema.length() - 1) : schema;
			if (StringUtil.isName(s) == false) {
				throw new IllegalArgumentException("@schema:value 中value必须是1个单词！");
			}
		}
		this.schema = schema;
		return this;
	}
	/**请求传进来的Table名
	 * @return
	 * @see {@link #getSQLTable()}
	 */
	@Override
	public String getTable() {
		return table;
	}
	/**数据库里的真实Table名
	 * 通过 {@link #TABLE_KEY_MAP} 映射
	 * @return
	 */
	@JSONField(serialize = false)
	@Override
	public String getSQLTable() {
		return (TABLE_KEY_MAP.containsKey(table) ? TABLE_KEY_MAP.get(table) : table)
				+ ( isKeyPrefix() ? " AS " + getAlias() : "");
	}
	@JSONField(serialize = false)
	@Override
	public String getTablePath() {
		return getSchema() + "." + getSQLTable();
	}
	@Override
	public AbstractSQLConfig setTable(String table) { //Table已经在Parser中校验，所以这里不用防SQL注入
		this.table = table;
		return this;
	}
	@Override
	public String getAlias() {
		if (StringUtil.isEmpty(alias, true)) {
			alias = getTable();
		}
		return alias;
	}
	@Override
	public AbstractSQLConfig setAlias(String alias) {
		this.alias = alias;
		return this;
	}

	@Override
	public String getGroup() {
		return group;
	}
	public AbstractSQLConfig setGroup(String... keys) {
		return setGroup(StringUtil.getString(keys));
	}
	@Override
	public AbstractSQLConfig setGroup(String group) {
		this.group = group;
		return this;
	}
	@JSONField(serialize = false)
	public String getGroupString() {
		//TODO 加上子表的group

		group = StringUtil.getTrimedString(group);
		if (group.isEmpty()) {
			return "";
		}

		String[] keys = StringUtil.split(group);
		if (keys == null || keys.length <= 0) {
			return "";
		}

		for (int i = 0; i < keys.length; i++) {
			if (isPrepared()) { //不能通过 ? 来代替，因为SQLExecutor statement.setString后 GROUP BY 'userId' 有单引号，只能返回一条数据，必须去掉单引号才行！
				if (StringUtil.isName(keys[i]) == false) {
					throw new IllegalArgumentException("@group:value 中 value里面用 , 分割的每一项都必须是1个单词！并且不要有空格！");
				}
			}

			keys[i] = getKey(keys[i]);
		}

		return " GROUP BY " + StringUtil.getString(keys);
	}

	@Override
	public String getHaving() {
		return having;
	}
	public AbstractSQLConfig setHaving(String... conditions) {
		return setHaving(StringUtil.getString(conditions));
	}
	@Override
	public AbstractSQLConfig setHaving(String having) {
		this.having = having;
		return this;
	}
	/**
	 * @return HAVING conditoin0 AND condition1 OR condition2 ...
	 */
	@JSONField(serialize = false)
	public String getHavingString() {
		having = StringUtil.getTrimedString(having);
		if(having.isEmpty()) {
			return ""; 
		}

		String[] keys = StringUtil.split(having, ";");
		if (keys == null || keys.length <= 0) {
			return "";
		}

		String expression;
		String method;
		//暂时不允许 String prefix;
		String suffix;

		//fun0(arg0,arg1,...);fun1(arg0,arg1,...)
		for (int i = 0; i < keys.length; i++) {

			//fun(arg0,arg1,...)
			expression = keys[i];

			//TODO 支持 maxId>=100 这种没括号的
			int start = expression.indexOf("(");
			int end = expression.indexOf(")");
			if (start >= end) {
				throw new IllegalArgumentException("字符 " + expression + " 不合法！@having:value 中 value 里的 SQL函数必须为 function(arg0,arg1,...) 这种格式！");
			}

			method = expression.substring(0, start);

			if (StringUtil.isName(method) == false) {
				throw new IllegalArgumentException("字符 " + method + " 不合法！"
						+ "预编译模式下 @having:\"function0(arg0,arg1,...)operator value;function1(arg0,arg1,...)operator value\""
						+ " 中SQL函数名 function 必须符合正则表达式 ^[0-9a-zA-Z_]+$ ！");
			}

			suffix = expression.substring(end + 1, expression.length());

			if (isPrepared() && PATTERN_RANGE.matcher((String) suffix).matches() == false) {
				throw new UnsupportedOperationException("字符串 " + suffix + " 不合法！"
						+ "预编译模式下 @having:\"function0(arg0,arg1,...)operator value;function1(arg0,arg1,...)operator value\""
						+ " 中 condition 必须符合正则表达式 ^[0-9%!=<>,]+$ ！不允许空格！");
			}

			String[] ckeys = StringUtil.split(expression.substring(start + 1, end));

			for (int j = 0; j < ckeys.length; j++) {

				if (isPrepared() && (StringUtil.isName(ckeys[j]) == false || ckeys[j].startsWith("_"))) {
					throw new IllegalArgumentException("字符 " + ckeys[j] + " 不合法！"
							+ "预编译模式下 @having:\"function0(arg0,arg1,...)operator value;function1(arg0,arg1,...)operator value\""
							+ " 中所有 arg 都必须是1个不以 _ 开头的单词！并且不要有空格！");
				}

				ckeys[j] = getKey(ckeys[j]);
			}

			keys[i] = method + "(" + StringUtil.getString(ckeys) + ")" + suffix;
		}

		return " HAVING " + StringUtil.getString(keys, AND); //TODO 支持 OR, NOT 参考 @combine:"&key0,|key1,!key2"
	}

	@Override
	public String getOrder() {
		return order;
	}
	public AbstractSQLConfig setOrder(String... conditions) {
		return setOrder(StringUtil.getString(conditions));
	}
	@Override
	public AbstractSQLConfig setOrder(String order) {
		this.order = order;
		return this;
	}
	@JSONField(serialize = false)
	public String getOrderString() {
		//TODO 加上子表的order

		order = StringUtil.getTrimedString(order);
		if (order.isEmpty()) {
			return "";
		}
		if (order.contains("+")) {//replace没有包含的replacement会崩溃
			order = order.replaceAll("\\+", " ASC ");
		}
		if (order.contains("-")) {
			order = order.replaceAll("-", " DESC ");
		}

		//TODO  column, order, group 都改用 List<String> 存储！！！，并且每个字段都要加 Table. 前缀！
		String[] keys = StringUtil.split(order);
		if (keys == null || keys.length <= 0) {
			return "";
		}

		String origin;
		String sort;
		int index;
		for (int i = 0; i < keys.length; i++) {
			index = keys[i].trim().endsWith(" ASC") ? keys[i].lastIndexOf(" ASC") : -1; //StringUtil.split返回数组中，子项不会有null
			if (index < 0) {
				index = keys[i].trim().endsWith(" DESC") ? keys[i].lastIndexOf(" DESC") : -1;
				sort = index <= 0 ? "" : " DESC ";
			} else {
				sort = " ASC ";
			}
			origin = index < 0 ? keys[i] : keys[i].substring(0, index);

			if (isPrepared()) { //不能通过 ? 来代替，SELECT 'id','name' 返回的就是 id:"id", name:"name"，而不是数据库里的值！
				//这里既不对origin trim，也不对 ASC/DESC ignoreCase，希望前端严格传没有任何空格的字符串过来，减少传输数据量，节约服务器性能
				if (StringUtil.isName(origin) == false) {
					throw new IllegalArgumentException("预编译模式下 @order:value 中 value里面用 , 分割的每一项"
							+ " column+ / column- 中 column必须是1个单词！并且不要有多余的空格！");
				}
			}
			keys[i] = getKey(origin) + sort;
		}

		return " ORDER BY " + StringUtil.getString(keys);
	}



	@Override
	public String getColumn() {
		return column;
	}
	public AbstractSQLConfig setColumn(String... keys) {
		return setColumn(StringUtil.getString(keys));
	}
	@Override
	public AbstractSQLConfig setColumn(String column) {
		this.column = column;
		return this;
	}
	@JSONField(serialize = false)
	public String getColumnString() throws Exception {
		switch (getMethod()) {
		case HEAD:
		case HEADS: //StringUtil.isEmpty(column, true) || column.contains(",") 时SQL.count(column)会return "*"
			if (isPrepared() && StringUtil.isEmpty(column, true) == false
			&& column.contains(",") == false && StringUtil.isName(column) == false) {
				throw new IllegalArgumentException("HEAD请求: @column:value 中 value里面用 , 分割的每一项都必须是1个单词！");
			}
			return SQL.count(column);
		case POST:
			if (StringUtil.isEmpty(column, true)) {
				throw new NotExistException(TAG + "getColumnString  getMethod() = POST"
						+ " >> StringUtil.isEmpty(column, true)");
			}

			if (isPrepared()) { //不能通过 ? 来代替，SELECT 'id','name' 返回的就是 id:"id", name:"name"，而不是数据库里的值！
				String[] keys = StringUtil.split(column);
				if (keys != null && keys.length > 0) {
					for (int i = 0; i < keys.length; i++) {
						if (StringUtil.isName(keys[i]) == false) {
							throw new IllegalArgumentException("POST请求: 每一个 key:value 中的key都必须是1个单词！");
						}
					}
				}
			}

			return "(" + column + ")";
		case GET:
		case GETS:
			boolean isQuery = RequestMethod.isQueryMethod(method);
			String joinColumn = "";
			if (isQuery && joinList != null) {
				SQLConfig c;
				boolean first = true;
				for (Join j : joinList) {
					c = j.getJoinConfig();
					c.setAlias(c.getTable());
					joinColumn += (first ? "" : ", ") + ((AbstractSQLConfig) c).getColumnString();

					first = false;
				}
			}

			String tableAlias = getAlias();

			column = StringUtil.getString(column);
			if (column.isEmpty()) {
				return isKeyPrefix() == false ? "*" : (tableAlias + ".*" + (StringUtil.isEmpty(joinColumn, true) ? "" : ", " + joinColumn));
			}

			String c = column;
			//			if (isPrepared()) { //不能通过 ? 来代替，SELECT 'id','name' 返回的就是 id:"id", name:"name"，而不是数据库里的值！
			String[] keys = StringUtil.split(column);
			if (keys != null && keys.length > 0) {
				String origin;
				String alias;
				int index;
				for (int i = 0; i < keys.length; i++) {
					index = keys[i].indexOf(":"); //StringUtil.split返回数组中，子项不会有null
					origin = index < 0 ? keys[i] : keys[i].substring(0, index);
					alias = index < 0 ? null : keys[i].substring(index + 1);

					if (isPrepared()) {
						if (StringUtil.isName(origin) == false || (alias != null && StringUtil.isName(alias) == false)) {
							throw new IllegalArgumentException("GET请求: 预编译模式下 @column:value 中 value里面用 , 分割的每一项"
									+ " column:alias 中 column必须是1个单词！如果有alias，则alias也必须为1个单词！并且不要有多余的空格！");
						}
					}

					if (isKeyPrefix()) {
						keys[i] = tableAlias + "." + origin + " AS `" + (isMain() ? "" : tableAlias + ".") + (StringUtil.isEmpty(alias, true) ? origin : alias) + "`";
					} else {
						keys[i] = origin + (StringUtil.isEmpty(alias, true) ? "" : " AS `" + alias + "`");
					}
				}
				//				}

				c = StringUtil.getString(keys);
			}

			return c.contains(":") == false ? c : c.replaceAll(":", " AS ") + (StringUtil.isEmpty(joinColumn, true) ? "" : ", " + joinColumn);//不能在这里改，后续还要用到:

		default:
			throw new UnsupportedOperationException("服务器内部错误：getColumnString 不支持 " + RequestMethod.getName(method) + " 等 [GET,GETS,HEAD,HEADS,POST] 外的ReuqestMethod！");
		}
	}


	@Override
	public String getValues() {
		return values;
	}
	@JSONField(serialize = false)
	public String getValuesString() {
		return values;
	}
	public AbstractSQLConfig setValues(Object[][] valuess) {
		String s = "";
		if (valuess != null && valuess.length > 0) {
			Object[] items = new Object[valuess.length];
			Object[] vs;
			for (int i = 0; i < valuess.length; i++) {
				vs = valuess[i];
				if (vs == null) {
					continue;
				}

				items[i] = "(";
				for (int j = 0; j < vs.length; j++) {
					items[i] += ((j <= 0 ? "" : ",") + getValue(vs[j]));
				}
				items[i] += ")";
			}
			s = StringUtil.getString(items);
		}
		return setValues(s);
	}
	@Override
	public AbstractSQLConfig setValues(String values) {
		this.values = values;
		return this;
	}

	@Override
	public Map<String, Object> getContent() {
		return content;
	}
	@Override
	public AbstractSQLConfig setContent(Map<String, Object> content) {
		this.content = content;
		return this;
	}

	@Override
	public int getCount() {
		return count;
	}
	@Override
	public AbstractSQLConfig setCount(int count) {
		this.count = count;
		return this;
	}
	@Override
	public int getPage() {
		return page;
	}
	@Override
	public AbstractSQLConfig setPage(int page) {
		this.page = page;
		return this;
	}
	@Override
	public int getPosition() {
		return position;
	}
	@Override
	public AbstractSQLConfig setPosition(int position) {
		this.position = position;
		return this;
	}

	@Override
	public int getQuery() {
		return query;
	}
	@Override
	public AbstractSQLConfig setQuery(int query) {
		this.query = query;
		return this;
	}
	@Override
	public int getType() {
		return type;
	}
	@Override
	public AbstractSQLConfig setType(int type) {
		this.type = type;
		return this;
	}
	@Override
	public List<Join> getJoinList() {
		return joinList;
	}
	@Override
	public SQLConfig setJoinList(List<Join> joinList) {
		this.joinList = joinList;
		return this;
	}

	@Override
	public boolean isTest() {
		return test;
	}
	@Override
	public AbstractSQLConfig setTest(boolean test) {
		this.test = test;
		return this;
	}
	@Override
	public boolean isCacheStatic() {
		return cacheStatic;
	}
	@Override
	public AbstractSQLConfig setCacheStatic(boolean cacheStatic) {
		this.cacheStatic = cacheStatic;
		return this;
	}


	/**获取初始位置offset
	 * @return
	 */
	@JSONField(serialize = false)
	public int getOffset() {
		return getOffset(getPage(), getCount());
	}
	/**获取初始位置offset
	 * @param page
	 * @param count
	 * @return
	 */
	public static int getOffset(int page, int count) {
		return page*count;
	}
	/**获取限制数量
	 * @return
	 */
	@JSONField(serialize = false)
	public String getLimitString() {
		return getLimitString(getPage(), getCount());// + 1);
	}
	/**获取限制数量
	 * @param limit
	 * @return
	 */
	public static String getLimitString(int page, int count) {
		return count <= 0 ? "" : " LIMIT " + getOffset(page, count) + ", " + count;
	}

	//WHERE <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	@Override
	public Map<String, Object> getWhere() {
		return where;
	}
	@Override
	public AbstractSQLConfig setWhere(Map<String, Object> where) {
		this.where = where;
		return this;
	}
	@NotNull
	@Override
	public Map<String, List<String>> getCombine() {
		List<String> andList = combine == null ? null : combine.get("&");
		if (andList == null) {
			andList = where == null ? new ArrayList<String>() : new ArrayList<String>(where.keySet());
			if (combine == null) {
				combine = new HashMap<>();
			}
			combine.put("&", andList);
		}
		return combine;
	}
	@Override
	public AbstractSQLConfig setCombine(Map<String, List<String>> combine) {
		this.combine = combine;
		return this;
	}
	/**
	 * noFunctionChar = false
	 * @param key
	 * @return
	 */
	@JSONField(serialize = false)
	@Override
	public Object getWhere(String key) {
		return getWhere(key, false);
	}
	/**
	 * @param key
	 * @param exactMatch
	 * @return
	 */
	@JSONField(serialize = false)
	@Override
	public Object getWhere(String key, boolean exactMatch) {
		if (exactMatch) {
			return where == null ? null : where.get(key);
		}

		Set<String> set = key == null || where == null ? null : where.keySet();
		if (set != null) {
			synchronized (where) {
				if (where != null) {
					int index;
					for (String k : set) {
						index = k.indexOf(key);
						if (index >= 0 && StringUtil.isName(k.substring(index)) == false) {
							return where.get(k);
						}
					}
				}
			}
		}
		return null;
	}
	@Override
	public AbstractSQLConfig putWhere(String key, Object value, boolean prior) {
		if (key != null) {
			if (where == null) {
				where = new LinkedHashMap<String, Object>();	
			}
			where.put(key, value);

			combine = getCombine();
			List<String> andList = combine == null ? null : combine.get("&");
			if (value == null) {
				andList.remove(key);
			}
			else if (andList == null || andList.contains(key) == false) {
				int i = 0;
				if (andList == null) {
					andList = new ArrayList<>();
				}
				else if (prior && andList.isEmpty() == false) {
					if (andList.contains(KEY_ID)) {
						i ++;
					}
					if (andList.contains(KEY_ID_IN)) {
						i ++;
					}
					if (andList.contains(KEY_USER_ID)) {
						i ++;
					}
					if (andList.contains(KEY_USER_ID_IN)) {
						i ++;
					}
				}

				if (prior) {
					andList.add(i, key); //userId的优先级不能比id高  0, key);
				} else {
					andList.add(key); //AbstractSQLExecutor.onPutColumn里getSQL，要保证缓存的SQL和查询的SQL里 where 的 key:value 顺序一致
				}
			}
			combine.put("&", andList);
		}
		return this;
	}

	/**获取WHERE
	 * @return
	 * @throws Exception 
	 */
	@JSONField(serialize = false)
	@Override
	public String getWhereString(boolean hasPrefix) throws Exception {
		return getWhereString(hasPrefix, getMethod(), getWhere(), getCombine(), getJoinList(), ! isTest());
	}
	/**获取WHERE
	 * @param method
	 * @param where
	 * @return
	 * @throws Exception 
	 */
	@JSONField(serialize = false)
	public String getWhereString(boolean hasPrefix, RequestMethod method, Map<String, Object> where, Map<String, List<String>> combine, List<Join> joinList, boolean verifyName) throws Exception {
		Set<Entry<String, List<String>>> combineSet = combine == null ? null : combine.entrySet();
		if (combineSet == null || combineSet.isEmpty()) {
			Log.w(TAG, "getWhereString  combineSet == null || combineSet.isEmpty() >> return \"\";");
			return "";
		}

		List<String> keyList;

		String whereString = "";

		boolean isCombineFirst = true;
		int logic;

		boolean isItemFirst;
		String c;
		String cs;

		for (Entry<String, List<String>> ce : combineSet) {
			keyList = ce == null ? null : ce.getValue();
			if (keyList == null || keyList.isEmpty()) {
				continue;
			}

			if ("|".equals(ce.getKey())) {
				logic = Logic.TYPE_OR;
			}
			else if ("!".equals(ce.getKey())) {
				logic = Logic.TYPE_NOT;
			}
			else {
				logic = Logic.TYPE_AND;
			}


			isItemFirst = true;
			cs = "";
			for (String key : keyList) {
				c = getWhereItem(key, where.get(key), method, verifyName);

				if (StringUtil.isEmpty(c, true)) {//避免SQL条件连接错误
					continue;
				}

				cs += (isItemFirst ? "" : (Logic.isAnd(logic) ? AND : OR)) + "(" + c + ")";

				isItemFirst = false;
			}


			whereString += (isCombineFirst ? "" : AND) + (Logic.isNot(logic) ? NOT : "") + " (  " + cs + "  ) ";
			isCombineFirst = false;
		}


		if (joinList != null) {

			SQLConfig jc;
			String js;
			//各种 JOIN 没办法统一用 & | ！连接，只能按优先级，和 @combine 一样?
			for (Join j : joinList) {
				switch (j.getJoinType()) {
				case "":
				case "|": //不支持 <>, [] ，避免太多符号
				case "&":
				case "!":
					logic = Logic.getType(j.getJoinType());

					jc = j.getJoinConfig();
					boolean isMain = jc.isMain();
					jc.setMain(false).setPrepared(isPrepared()).setPreparedValueList(new ArrayList<Object>());
					js = jc.getWhereString(false);
					jc.setMain(isMain);

					if (StringUtil.isEmpty(js, true)) {
						continue;
					}

					whereString = " ( "
							+ getCondition(
									Logic.isNot(logic), 
									whereString
									+ ( StringUtil.isEmpty(whereString, true) ? "" : (Logic.isAnd(logic) ? AND : OR) )
									+ " ( " + js + " ) "
									)
							+ " ) ";

					preparedValueList.addAll(jc.getPreparedValueList());
					break;
					//可能 LEFT JOIN 和 INNER JOIN 同时存在				default:
					//					throw new UnsupportedOperationException("");
				}
			}
		}

		String s = whereString.isEmpty() ? "" : (hasPrefix ? " WHERE " : "") + whereString;

		if (s.isEmpty() && RequestMethod.isQueryMethod(method) == false) {
			throw new UnsupportedOperationException("写操作请求必须带条件！！！");
		}

		return s;
	}

	/**
	 * @param key
	 * @param value
	 * @param method
	 * @param verifyName
	 * @return
	 * @throws Exception
	 */
	private String getWhereItem(String key, Object value
			, RequestMethod method, boolean verifyName) throws Exception {
		Log.d(TAG, "getWhereItem  key = " + key);
		//避免筛选到全部	value = key == null ? null : where.get(key);
		if (key == null || value == null || key.startsWith("@") || key.endsWith("()")) {//关键字||方法, +或-直接报错
			Log.d(TAG, "getWhereItem  key == null || value == null"
					+ " || key.startsWith(@) || key.endsWith(()) >> continue;");
			return null;
		}
		if (key.endsWith("@")) {//引用
			//	key = key.substring(0, key.lastIndexOf("@"));
			throw new IllegalArgumentException(TAG + ".getWhereItem: 字符 " + key + " 不合法！");
		}

		int keyType;
		if (key.endsWith("$")) {
			keyType = 1;
		} 
		else if (key.endsWith("?")) {
			keyType = 2;
		}
		else if (key.endsWith("{}")) {
			keyType = 3;
		}
		else if (key.endsWith("<>")) {
			keyType = 4;
		}
		else { //else绝对不能省，避免再次踩坑！ keyType = 0; 写在for循环外面都没注意！
			keyType = 0;
		}
		key = getRealKey(method, key, false, true, verifyName);

		switch (keyType) {
		case 1:
			return getSearchString(key, value);
		case 2:
			return getRegExpString(key, value);
		case 3:
			return getRangeString(key, value);
		case 4:
			return getContainString(key, value);
		default: //TODO MySQL JSON类型的字段对比 key='[]' 会无结果！ key LIKE '[1, 2, 3]'  //TODO MySQL , 后面有空格！
			return getEqualString(key, value);
		}
	}


	@JSONField(serialize = false)
	public String getEqualString(String key, Object value) {
		return getKey(key) + "=" + getValue(value);
	}

	public String getKey(String key) {
		return (isKeyPrefix() ? getAlias() + "." : "") + key;
	}

	/**
	 * 使用prepareStatement预编译，值为 ? ，后续动态set进去
	 */
	private List<Object> preparedValueList = new ArrayList<>();
	private Object getValue(@NotNull Object value) {
		if (isPrepared()) {
			preparedValueList.add(value);
			return "?";
		}
		return "'" + value + "'";
	}
	@Override
	public List<Object> getPreparedValueList() {
		return preparedValueList;
	}
	@Override
	public AbstractSQLConfig setPreparedValueList(List<Object> preparedValueList) {
		this.preparedValueList = preparedValueList;
		return this;
	}

	//$ search <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	/**search key match value
	 * @param in
	 * @return {@link #getSearchString(String, Object[], int)}
	 * @throws IllegalArgumentException 
	 */
	@JSONField(serialize = false)
	public String getSearchString(String key, Object value) throws IllegalArgumentException {
		if (value == null) {
			return "";
		}

		Logic logic = new Logic(key);
		key = logic.getKey();
		Log.i(TAG, "getSearchString key = " + key);

		JSONArray arr = newJSONArray(value);
		if (arr.isEmpty()) {
			return "";
		}
		return getSearchString(key, arr.toArray(), logic.getType());
	}
	/**search key match values
	 * @param in
	 * @return LOGIC [  key LIKE 'values[i]' ]
	 * @throws IllegalArgumentException 
	 */
	@JSONField(serialize = false)
	public String getSearchString(String key, Object[] values, int type) throws IllegalArgumentException {
		if (values == null || values.length <= 0) {
			return "";
		}

		String condition = "";
		for (int i = 0; i < values.length; i++) {
			if (values[i] instanceof String == false) {
				throw new IllegalArgumentException(key + "$\":value 中value的类型只能为String或String[]！");
			}
			condition += (i <= 0 ? "" : (Logic.isAnd(type) ? AND : OR)) + getLikeString(key, values[i]);
		}

		return getCondition(Logic.isNot(type), condition);
	}

	/**WHERE key LIKE 'value'
	 * @param key endsWith("!") ? key = key.substring(0, key.length() - 1) + NOT;
	 * @param value
	 * @return key LIKE 'value'
	 */
	@JSONField(serialize = false)
	public String getLikeString(String key, Object value) {
		return getKey(key) + " LIKE "  + getValue(value);
	}

	//$ search >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>



	//$ search <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	/**search key match RegExps value
	 * @param in
	 * @return {@link #getRegExpString(String, Object[], int)}
	 * @throws IllegalArgumentException 
	 */
	@JSONField(serialize = false)
	public String getRegExpString(String key, Object value) throws IllegalArgumentException {
		if (value == null) {
			return "";
		}

		Logic logic = new Logic(key);
		key = logic.getKey();
		Log.i(TAG, "getRegExpString key = " + key);

		JSONArray arr = newJSONArray(value);
		if (arr.isEmpty()) {
			return "";
		}
		return getRegExpString(key, arr.toArray(), logic.getType());
	}
	/**search key match RegExp values
	 * @param in
	 * @return LOGIC [  key REGEXP 'values[i]' ]
	 * @throws IllegalArgumentException 
	 */
	@JSONField(serialize = false)
	public String getRegExpString(String key, Object[] values, int type) throws IllegalArgumentException {
		if (values == null || values.length <= 0) {
			return "";
		}

		String condition = "";
		for (int i = 0; i < values.length; i++) {
			if (values[i] instanceof String == false) {
				throw new IllegalArgumentException(key + "$\":value 中value的类型只能为String或String[]！");
			}
			condition += (i <= 0 ? "" : (Logic.isAnd(type) ? AND : OR)) + getRegExpString(key, (String) values[i]);
		}

		return getCondition(Logic.isNot(type), condition);
	}

	/**WHERE key REGEXP 'value'
	 * @param key
	 * @param value
	 * @return key REGEXP 'value'
	 */
	@JSONField(serialize = false)
	public String getRegExpString(String key, String value) {
		return getKey(key) + " REGEXP " + getValue(value);
	}
	//$ search >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>



	//{} range <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	// * 和 / 不能同时出现，防止 /* */ 段注释！ # 和 -- 不能出现，防止行注释！ ; 不能出现，防止隔断SQL语句！空格不能出现，防止 CRUD,DROP,SHOW TABLES等语句！
	private static final Pattern PATTERN_RANGE;
	static {
		PATTERN_RANGE = Pattern.compile("^[0-9%!=<>,]+$"); // ^[a-zA-Z0-9_*%!=<>(),"]+$ 导致 exists(select*from(Comment)) 通过！
	}


	/**WHERE key > 'key0' AND key <= 'key1' AND ...
	 * @param key
	 * @param range "condition0,condition1..."
	 * @return key condition0 AND key condition1 AND ...
	 * @throws Exception 
	 */
	@JSONField(serialize = false)
	public String getRangeString(String key, Object range) throws Exception {
		Log.i(TAG, "getRangeString key = " + key);
		if (range == null) {//依赖的对象都没有给出有效值，这个存在无意义。如果是客户端传的，那就能在客户端确定了。
			throw new NotExistException(TAG + "getRangeString(" + key + ", " + range
					+ ") range == null");
		}

		Logic logic = new Logic(key);
		key = logic.getKey();
		Log.i(TAG, "getRangeString key = " + key);

		if (range instanceof List) {
			if (logic.isOr() || logic.isNot()) {
				return getKey(key) + getInString(key, ((List<?>) range).toArray(), logic.isNot());
			}
			throw new IllegalArgumentException(key + "{}\":[] 中key末尾的逻辑运算符只能用'|','!'中的一种 ！");
		}
		if (range instanceof String) {//非Number类型需要客户端拼接成 < 'value0', >= 'value1'这种
			if (isPrepared() && PATTERN_RANGE.matcher((String) range).matches() == false) {
				throw new UnsupportedOperationException("字符串 " + range + " 不合法！预编译模式下 key{}:\"condition\" 中 condition 必须符合正则表达式 ^[0-9%!=<>,]+$ ！不允许空格！");
			}

			String[] conditions = StringUtil.split((String) range);
			String condition = "";
			if (conditions != null) {
				int index;
				for (int i = 0; i < conditions.length; i++) {//对函数条件length(key)<=5这种不再在开头加key
					index = conditions[i] == null ? -1 : conditions[i].indexOf("(");
					condition += ((i <= 0 ? "" : (logic.isAnd() ? AND : OR))//连接方式
							+ (index >= 0 && index < conditions[i].indexOf(")") ? "" : getKey(key) + " ")//函数和非函数条件
							+ conditions[i]);//单个条件
				}
			}
			if (condition.isEmpty()) {
				return "";
			}

			return getCondition(logic.isNot(), condition);
		}

		throw new IllegalArgumentException(key + "{}:range 类型为" + range.getClass().getSimpleName()
				+ "！range只能是 用','分隔条件的字符串 或者 可取选项JSONArray！");
	}
	/**WHERE key IN ('key0', 'key1', ... )
	 * @param in
	 * @return IN ('key0', 'key1', ... )
	 * @throws NotExistException 
	 */
	@JSONField(serialize = false)
	public String getInString(String key, Object[] in, boolean not) throws NotExistException {
		String condition = "";
		if (in != null) {//返回 "" 会导致 id:[] 空值时效果和没有筛选id一样！
			for (int i = 0; i < in.length; i++) {
				condition += ((i > 0 ? "," : "") + getValue(in[i]));
			}
		}
		if (condition.isEmpty()) {//条件如果存在必须执行，不能忽略。条件为空会导致出错，又很难保证条件不为空(@:条件)，所以还是这样好
			throw new NotExistException(TAG + ".getInString(" + key + ", [], " + not
					+ ") >> condition.isEmpty() >> IN()");
		}
		return (not ? NOT : "") + " IN (" + condition + ")";
	}
	//{} range >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


	//<> contain <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	/**WHERE key contains value
	 * @param key
	 * @param value
	 * @return 	{@link #getContainString(String, Object[], int)}
	 * @throws NotExistException
	 */
	@JSONField(serialize = false)
	public String getContainString(String key, Object value) throws NotExistException {
		if (value == null) {
			return "";
		}

		Logic logic = new Logic(key);
		key = logic.getKey();
		Log.i(TAG, "getRangeString key = " + key);

		return getContainString(key, newJSONArray(value).toArray(), logic.getType());
	}
	/**WHERE key contains childs
	 * @param key
	 * @param childs null ? "" : (empty ? no child : contains childs)
	 * @param type |, &, !
	 * @return LOGIC [  ( key LIKE '[" + childs[i] + "]'  OR  key LIKE '[" + childs[i] + ", %'
	 *   OR  key LIKE '%, " + childs[i] + ", %'  OR  key LIKE '%, " + childs[i] + "]' )  ]
	 * @throws IllegalArgumentException 
	 */
	@JSONField(serialize = false)
	public String getContainString(String key, Object[] childs, int type) throws IllegalArgumentException {
		boolean not = Logic.isNot(type);
		String condition = "";
		if (childs != null) {
			for (int i = 0; i < childs.length; i++) {
				if (childs[i] != null) {
					if (childs[i] instanceof JSON) {
						throw new IllegalArgumentException(key + "<>\":value 中value类型不能为JSON！");
					}
					if (childs[i] instanceof String) {
						childs[i] = "\"" + childs[i] + "\"";
					}
					condition += (i <= 0 ? "" : (Logic.isAnd(type) ? AND : OR))
							+ "JSON_CONTAINS(" + getKey(key) + ", " + getValue(childs[i]) + ")";
				}
			}
			if (condition.isEmpty()) {
				condition = (SQL.isNull(key, true) + OR + getLikeString(key, "[]")); // key = '[]' 无结果！
			} else {
				condition = (SQL.isNull(key, false) + AND + "(" + condition + ")");
			}
		}
		if (condition.isEmpty()) {
			return "";
		}
		return getCondition(not, condition);
	}
	//<> contain >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

	/**拼接条件
	 * @param not
	 * @param condition
	 * @return
	 */
	private static String getCondition(boolean not, String condition) {
		return not ? NOT + "(" + condition + ")" : condition;
	}


	/**转为JSONArray
	 * @param tv
	 * @return
	 */
	@NotNull
	public static JSONArray newJSONArray(Object obj) {
		JSONArray array = new JSONArray();
		if (obj != null) {
			if (obj instanceof Collection) {
				array.addAll((Collection<?>) obj);
			} else {
				array.add(obj);
			}
		}
		return array;
	}

	//WHERE >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


	//SET <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	/**获取SET
	 * @return
	 * @throws Exception 
	 */
	@JSONField(serialize = false)
	public String getSetString() throws Exception {
		return getSetString(getMethod(), getContent(), ! isTest());
	}
	/**获取SET
	 * @param method
	 * @param content
	 * @return
	 * @throws Exception 
	 */
	@JSONField(serialize = false)
	public String getSetString(RequestMethod method, Map<String, Object> content, boolean verifyName) throws Exception {
		Set<String> set = content == null ? null : content.keySet();
		if (set != null && set.size() > 0) {
			String setString = "";
			boolean isFirst = true;
			int keyType = 0;// 0 - =; 1 - +, 2 - -
			Object value;
			for (String key : set) {
				//避免筛选到全部	value = key == null ? null : content.get(key);
				if (key == null || KEY_ID.equals(key)) {
					continue;
				}

				if (key.endsWith("+")) {
					keyType = 1;
				} else if (key.endsWith("-")) {
					keyType = 2;
				}
				value = content.get(key);
				key = getRealKey(method, key, false, true, verifyName);

				setString += (isFirst ? "" : ", ") + (key + "=" + (keyType == 1 ? getAddString(key, value) : (keyType == 2
						? getRemoveString(key, value) : getValue(value)) ) );

				isFirst = false;
			}
			if (setString.isEmpty()) {
				throw new NotExistException(TAG + "getSetString  >> setString.isEmpty()");
			}
			return " SET " + setString;
		}
		return "";
	}

	/**SET key = CONCAT (key, 'value')
	 * @param key
	 * @param value
	 * @return CONCAT (key, 'value')
	 * @throws IllegalArgumentException
	 */
	@JSONField(serialize = false)
	public String getAddString(String key, Object value) throws IllegalArgumentException {
		if (value instanceof Number) {
			return key + " + " + value;
		}
		if (value instanceof String) {
			return " CONCAT (" + key + ", " + getValue(value) + ") ";
		}
		throw new IllegalArgumentException(key + "+ 对应的值 " + value + " 不是Number,String,Array中的任何一种！");
	}
	/**SET key = replace(key, 'value', '')
	 * @param key
	 * @param value
	 * @return REPLACE (key, 'value', '')
	 * @throws IllegalArgumentException
	 */
	@JSONField(serialize = false)
	public String getRemoveString(String key, Object value) throws IllegalArgumentException {
		if (value instanceof Number) {
			return key + " - " + value;
		}
		if (value instanceof String) {
			return SQL.replace(key, (String) getValue(value), "");// " replace(" + key + ", '" + value + "', '') ";
		}
		throw new IllegalArgumentException(key + "- 对应的值 " + value + " 不是Number,String,Array中的任何一种！");
	}
	//SET >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


	/**
	 * @return
	 * @throws Exception 
	 */
	@JSONField(serialize = false)
	@Override
	public String getSQL(boolean prepared) throws Exception {
		return getSQL(this.setPrepared(prepared));
	}
	/**
	 * @param config
	 * @return
	 * @throws Exception 
	 */
	public static String getSQL(AbstractSQLConfig config) throws Exception {
		String tablePath = config == null ? null : config.getTablePath();
		if (StringUtil.isNotEmpty(tablePath, true) == false) {
			Log.i(TAG, "getSQL  StringUtil.isNotEmpty(tablePath, true) == false >> return null;");
			return null;
		}

		switch (config.getMethod()) {
		case POST:
			return "INSERT INTO " + tablePath + config.getColumnString() + " VALUES" + config.getValuesString();
		case PUT:
			return "UPDATE " + tablePath + config.getSetString() + config.getWhereString(true);
		case DELETE:
			return "DELETE FROM " + tablePath + config.getWhereString(true);
		default:
			config.setPreparedValueList(new ArrayList<Object>());
			String column = config.getColumnString();
			return "SELECT " + column + " FROM " + getConditionString(column, tablePath, config);
		}
	}

	/**获取条件SQL字符串
	 * @param page 
	 * @param column
	 * @param table
	 * @param where
	 * @return
	 * @throws Exception 
	 */
	private static String getConditionString(String column, String table, AbstractSQLConfig config) throws Exception {
		String where = config.getWhereString(true);

		String condition = table + config.getJoinString() + where + (
				RequestMethod.isGetMethod(config.getMethod(), true) == false ?
						"" : config.getGroupString() + config.getHavingString() + config.getOrderString()
				)
				; //+ config.getLimitString();

		//no need to optimize
		//		if (config.getPage() <= 0 || ID.equals(column.trim())) {
		return condition + config.getLimitString();
		//		}
		//
		//
		//		//order: id+ -> id >= idOfStartIndex; id- -> id <= idOfStartIndex <<<<<<<<<<<<<<<<<<<
		//		String order = StringUtil.getNoBlankString(config.getOrder());
		//		List<String> orderList = order.isEmpty() ? null : Arrays.asList(StringUtil.split(order));
		//
		//		int type = 0;
		//		if (BaseModel.isEmpty(orderList) || BaseModel.isContain(orderList, ID+"+")) {
		//			type = 1;
		//		}
		//		else if (BaseModel.isContain(orderList, ID+"-")) {
		//			type = 2;
		//		}
		//
		//		if (type > 0) {
		//			return condition.replace("WHERE",
		//					"WHERE id " + (type == 1 ? ">=" : "<=") + " (SELECT id FROM " + table
		//					+ where + " ORDER BY id " + (type == 1 ? "ASC" : "DESC") + " LIMIT " + config.getOffset() + ", 1) AND"
		//					)
		//					+ " LIMIT " + config.getCount(); //子查询起始id不一定准确，只能作为最小可能！ ;//
		//		}
		//		//order: id+ -> id >= idOfStartIndex; id- -> id <= idOfStartIndex >>>>>>>>>>>>>>>>>>
		//
		//
		//		//结果错误！SELECT * FROM User AS t0 INNER JOIN
		//      (SELECT id FROM User ORDER BY date ASC LIMIT 20, 10) AS t1 ON t0.id = t1.id
		//		//common case, inner join
		//		condition += config.getLimitString();
		//		return table + " AS t0 INNER JOIN (SELECT id FROM " + condition + ") AS t1 ON t0.id = t1.id";
	}


	private boolean keyPrefix;
	@Override
	public boolean isKeyPrefix() {
		return keyPrefix;
	}
	@Override
	public AbstractSQLConfig setKeyPrefix(boolean keyPrefix) {
		this.keyPrefix = keyPrefix;
		return this;
	}



	public String getJoinString() throws Exception {
		String joinOns = "";
		if (joinList != null) {
			String sql = null;
			SQLConfig jc;
			for (Join j : joinList) {

				//LEFT JOIN sys.apijson_user AS User ON User.id = Moment.userId， 都是用 = ，通过relateType处理缓存
				// <"INNER JOIN User ON User.id = Moment.userId", UserConfig>  TODO  AS 放 getSQLTable 内
				jc = j.getJoinConfig();
				jc.setPrepared(isPrepared());

				switch (j.getJoinType()) {
				case "":
				case "|": //不支持 <>, [] ，避免太多符号
				case "&":
				case "!":
					sql = " INNER JOIN " + jc.getTablePath() + " ON " + jc.getTable() + "." + j.getKey() + " = "
							+ j.getTargetName() + "." + j.getTargetKey();
					break;
				case "<":
				case ">":
					jc.setMain(true).setKeyPrefix(false);
					sql = ( ">".equals(j.getJoinType()) ? " RIGHT" : " LEFT") + " JOIN ( " + jc.getSQL(isPrepared()) + " ) AS "
							+ j.getName() + " ON " + jc.getTable() + "." + j.getKey() + " = "
							+ j.getTargetName() + "." + j.getTargetKey();
					jc.setMain(false).setKeyPrefix(true);

					preparedValueList.addAll(jc.getPreparedValueList());
					break;
				default:
					throw new UnsupportedOperationException("服务器内部错误：不支持JOIN类型 " + type + " !");
				}

				joinOns += "  \n  " + sql;
			}
		}
		return joinOns;
	}

	/**获取查询配置
	 * @param table
	 * @param request
	 * @return
	 * @throws Exception 
	 */
	public static AbstractSQLConfig newSQLConfig(RequestMethod method, String table, JSONObject request, List<Join> joinList, Callback callback) throws Exception {
		if (request == null) { // User:{} 这种空内容在查询时也有效
			throw new NullPointerException(TAG + ": newSQLConfig  request == null!");
		}
		AbstractSQLConfig config = callback.getSQLConfig(method, table);

		if (request.isEmpty()) { // User:{} 这种空内容在查询时也有效
			return config; //request.remove(key); 前都可以直接return，之后必须保证 put 回去
		}

		Object idIn = request.get(KEY_ID_IN); //可能是 id{}:">0"

		if (method == POST) {
			if (idIn != null) { //不能在这里确定[]的长度，只能在外面传进来
				if ((idIn instanceof List == false) || ((List<?>)idIn).isEmpty()) { // id{}:[] 表示同时插入多条记录
					throw new IllegalArgumentException("POST请求，生成多条记录请用 id{}:[] ！ [] 类型为JSONArray且不能为空！");
				}
			} else if (request.get(KEY_ID) == null) {
				request.put(KEY_ID, System.currentTimeMillis());
			}
		}

		//对id和id{}处理，这两个一定会作为条件
		Long id = request.getLong(KEY_ID);
		if (id != null) { //null无效
			if (id <= 0) { //一定没有值
				throw new NotExistException(TAG + ": newSQLConfig " + table + ".id <= 0");
			}

			if (idIn != null && idIn instanceof List) { //共用idArr场景少性能差
				if (idIn != null && ((List<?>) idIn).contains(id) == false) {//empty有效  BaseModel.isEmpty(idArr) == false) {
					Log.w(TAG, "newSQLConfig  id > 0 >> idInObj != null && idInObj.contains(id) == false >> return null;");
					throw new NotExistException(TAG + ": newSQLConfig  idIn != null && ((JSONArray) idIn).contains(id) == false");
				}
			}
		}


		String role = request.getString(KEY_ROLE);
		String schema = request.getString(KEY_SCHEMA);
		String combine = request.getString(KEY_COMBINE);
		String column = request.getString(KEY_COLUMN);
		String group = request.getString(KEY_GROUP);
		String having = request.getString(KEY_HAVING);
		String order = request.getString(KEY_ORDER);

		//强制作为条件且放在最前面优化性能
		request.remove(KEY_ID);
		request.remove(KEY_ID_IN);
		//关键词
		request.remove(KEY_ROLE);
		request.remove(KEY_SCHEMA);
		request.remove(KEY_COMBINE);
		request.remove(KEY_COLUMN);
		request.remove(KEY_GROUP);
		request.remove(KEY_HAVING);
		request.remove(KEY_ORDER);


		Map<String, Object> tableWhere = new LinkedHashMap<String, Object>();//保证顺序好优化 WHERE id > 1 AND name LIKE...

		//已经remove了id和id{}，以及@key
		Set<String> set = request.keySet(); //前面已经判断request是否为空
		if (method == POST) {//POST操作
			if (set != null && set.isEmpty() == false) { //不能直接return，要走完下面的流程
				List<Object> idList;
				if (id != null) { //单条记录
					if (idIn != null) {
						throw new IllegalArgumentException("POST请求中 id 和 id{} 不能同时存在!");
					}

					idList = new ArrayList<Object>(1);
					idList.add(id);
				} else { //多条记录
					idList = new ArrayList<Object>((JSONArray) idIn);
				}

				//idIn不为空时，valuesString有多条，唯一的区别就是id
				String[] columns = set.toArray(new String[]{});

				Collection<Object> valueCollection = request.values();
				Object[] values = valueCollection == null ? null : valueCollection.toArray();

				if (values == null || values.length != columns.length) {
					throw new Exception("服务器内部错误:\n" + TAG
							+ " newSQLConfig  values == null || values.length != columns.length !");
				}
				column = KEY_ID + "," + StringUtil.getString(columns); //set已经判断过不为空
				final int size = columns.length + 1; //以key数量为准

				Object[][] valuess = new Object[idList.size()][]; // [idList.size()][]
				Object[] items; //(item0, item1, ...)
				for (int i = 0; i < idList.size(); i++) {
					items = new Object[size];
					items[0] = idList.get(i); //第0个就是id
					for (int j = 1; j < size; j++) {
						items[j] = values[j-1]; //从第1个开始，允许"null"
					}
					valuess[i] = items;
				}
				config.setValues(valuess);
			}
		} 
		else { //非POST操作
			final boolean isWhere = method != PUT;//除了POST,PUT，其它全是条件！！！

			//条件<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
			List<String> whereList = null;

			Map<String, List<String>> combineMap = new LinkedHashMap<>();
			List<String> andList = new ArrayList<>();
			List<String> orList = new ArrayList<>();
			List<String> notList = new ArrayList<>();

			//强制作为条件且放在最前面优化性能
			if (id != null) {
				tableWhere.put(KEY_ID, id);
				andList.add(KEY_ID);
			}
			if (idIn != null) {
				tableWhere.put(KEY_ID_IN, idIn);
				andList.add(KEY_ID_IN);
			}

			String[] ws = StringUtil.split(combine);
			if (ws != null) {
				if (method == DELETE || method == GETS || method == HEADS) {
					throw new IllegalArgumentException("DELETE,GETS,HEADS 请求不允许传 @combine:\"conditons\" !");
				}
				whereList = new ArrayList<>();

				String w;
				for (int i = 0; i < ws.length; i++) { //去除 &,|,! 前缀
					w = ws[i];
					if (w != null) {
						if (w.startsWith("&")) {
							w = w.substring(1);
							andList.add(w);
						}
						else if (w.startsWith("|")) {
							if (method == PUT) {
								throw new IllegalArgumentException(table + ":{} 里的 @combine:value 中的value里条件 " + ws[i] + " 不合法！"
										+ "PUT请求的 @combine:\"key0,key1,...\" 不允许传 |key 或 !key !");
							}
							w = w.substring(1);
							orList.add(w);
						}
						else if (w.startsWith("!")) {
							if (method == PUT) {
								throw new IllegalArgumentException(table + ":{} 里的 @combine:value 中的value里条件 " + ws[i] + " 不合法！"
										+ "PUT请求的 @combine:\"key0,key1,...\" 不允许传 |key 或 !key !");
							}
							w = w.substring(1);
							notList.add(w);
						}
						else {
							orList.add(w);
						}

						if (w.isEmpty()) {
							throw new IllegalArgumentException(table + ":{} 里的 @combine:value 中的value里条件 " + ws[i] + " 不合法！不允许为空值！");
						}
						else {
							if (KEY_ID.equals(w) || KEY_ID_IN.equals(w) || KEY_USER_ID.equals(w) || KEY_USER_ID_IN.equals(w)) {
								throw new UnsupportedOperationException(table + ":{} 里的 @combine:value 中的value里 " + ws[i] + " 不合法！"
										+ "不允许传 [" + KEY_ID + ", " + KEY_ID_IN + ", " + KEY_USER_ID + ", " + KEY_USER_ID_IN + "] 其中任何一个！");
							}
						}

						whereList.add(w);
					}
					if (request.containsKey(w) == false) {
						throw new IllegalArgumentException(table + ":{} 里的 @combine:value 中的value里 " + ws[i] + " 对应的 " + w + " 不在它里面！");
					}
				}

			}

			//条件>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

			Map<String, Object> tableContent = new LinkedHashMap<String, Object>();
			Object value;
			for (String key : set) {
				value = request.get(key);

				if (value instanceof Map) {//只允许常规Object
					throw new IllegalArgumentException("不允许 " + key + " 等任何key的value类型为 {JSONObject} !");
				}

				//解决AccessVerifier新增userId没有作为条件，而是作为内容，导致PUT，DELETE出错
				if (isWhere) {
					tableWhere.put(key, value);
					if (whereList == null || whereList.contains(key) == false) {
						andList.add(key);
					}
				}
				else if (whereList != null && whereList.contains(key)) {
					tableWhere.put(key, value);
				}
				else {
					tableContent.put(key, value);//一样 instanceof JSONArray ? JSON.toJSONString(value) : value);
				}
			}

			combineMap.put("&", andList);
			combineMap.put("|", orList);
			combineMap.put("!", notList);
			config.setCombine(combineMap);

			config.setContent(tableContent);
		}


		config.setWhere(tableWhere);					

		config.setId(id == null ? 0 : id);
		//在	tableWhere 第0个		config.setIdIn(idIn);

		config.setRole(role);
		config.setSchema(schema);
		config.setColumn(column);
		config.setGroup(group);
		config.setHaving(having);
		config.setOrder(order);
		config.setJoinList(parseJoin(method, joinList, callback));

		config.setKeyPrefix(RequestMethod.isQueryMethod(method) && (config.isMain() == false || (joinList != null && joinList.isEmpty() == false)));

		//TODO 解析JOIN，包括 @column，@group 等要合并

		//后面还可能用到，要还原
		//id或id{}条件
		request.put(KEY_ID, id);
		request.put(KEY_ID_IN, idIn);
		//关键词
		request.put(KEY_ROLE, role);
		request.put(KEY_SCHEMA, schema);
		request.put(KEY_COMBINE, combine);
		request.put(KEY_COLUMN, column);
		request.put(KEY_GROUP, group);
		request.put(KEY_HAVING, having);
		request.put(KEY_ORDER, order);

		return config;
	}


	public static List<Join> parseJoin(RequestMethod method, List<Join> joinList, Callback callback) throws Exception {

		//TODO 解析出 SQLConfig 再合并 column, order, group 等
		if (joinList == null || joinList.isEmpty()) {
			return null;
		}


		String name;
		for (Join j : joinList) {
			name = j.getName();
			SQLConfig joinConfig = newSQLConfig(method, name, j.getTable(), null, callback).setMain(false).setKeyPrefix(true);
			SQLConfig cacheConfig = newSQLConfig(method, name, j.getTable(), null, callback).setCount(1);

			j.setJoinConfig(joinConfig);
			j.setCacheConfig(cacheConfig);
		}

		return joinList;
	}





	/**获取客户端实际需要的key
	 * verifyName = true
	 * @param method
	 * @param originKey
	 * @param isTableKey
	 * @param saveLogic 保留逻辑运算符 & | !
	 * @return
	 */
	public static String getRealKey(RequestMethod method, String originKey
			, boolean isTableKey, boolean saveLogic) throws Exception {
		return getRealKey(method, originKey, isTableKey, saveLogic, true);
	}
	/**获取客户端实际需要的key
	 * @param method
	 * @param originKey
	 * @param isTableKey
	 * @param saveLogic 保留逻辑运算符 & | !
	 * @param verifyName 验证key名是否符合代码变量/常量名
	 * @return
	 */
	public static String getRealKey(RequestMethod method, String originKey
			, boolean isTableKey, boolean saveLogic, boolean verifyName) throws Exception {
		Log.i(TAG, "getRealKey  saveLogic = " + saveLogic + "; originKey = " + originKey);
		if (originKey == null || originKey.startsWith("`") || zuo.biao.apijson.JSONObject.isArrayKey(originKey)) {
			Log.w(TAG, "getRealKey  originKey == null || originKey.startsWith(`)"
					+ " || zuo.biao.apijson.JSONObject.isArrayKey(originKey) >>  return originKey;");
			return originKey;
		}

		String key = new String(originKey);
		if (key.endsWith("$")) {//搜索，查询时处理
			key = key.substring(0, key.length() - 1);
		}
		else if (key.endsWith("?")) {//匹配正则表达式，查询时处理
			key = key.substring(0, key.length() - 1);
		}
		else if (key.endsWith("{}")) {//被包含，或者说key对应值处于value的范围内。查询时处理
			key = key.substring(0, key.length() - 2);
		} 
		else if (key.endsWith("<>")) {//包含，或者说value处于key对应值的范围内。查询时处理
			key = key.substring(0, key.length() - 2);
		} 
		else if (key.endsWith("()")) {//方法，查询完后处理，先用一个Map<key,function>保存？
			key = key.substring(0, key.length() - 2);
		} 
		else if (key.endsWith("@")) {//引用，引用对象查询完后处理。fillTarget中暂时不用处理，因为非GET请求都是由给定的id确定，不需要引用
			key = key.substring(0, key.length() - 1);
		}
		else if (key.endsWith("+")) {//延长，PUT查询时处理
			if (method == PUT) {//不为PUT就抛异常
				key = key.substring(0, key.length() - 1);
			}
		} 
		else if (key.endsWith("-")) {//缩减，PUT查询时处理
			if (method == PUT) {//不为PUT就抛异常
				key = key.substring(0, key.length() - 1);
			}
		}

		String last = null;//不用Logic优化代码，否则 key 可能变为 key| 导致 key=value 变成 key|=value 而出错
		if (RequestMethod.isQueryMethod(method)) {//逻辑运算符仅供GET,HEAD方法使用
			last = key.isEmpty() ? "" : key.substring(key.length() - 1);
			if ("&".equals(last) || "|".equals(last) || "!".equals(last)) {
				key = key.substring(0, key.length() - 1);
			} else {
				last = null;//避免key + StringUtil.getString(last)错误延长
			}
		}

		//"User:toUser":User转换"toUser":User, User为查询同名Table得到的JSONObject。交给客户端处理更好
		if (isTableKey) {//不允许在column key中使用Type:key形式
			key = Pair.parseEntry(key, true).getKey();//table以左边为准
		} else {
			key = Pair.parseEntry(key).getValue();//column以右边为准
		}

		if (verifyName && StringUtil.isName(key.startsWith("@") ? key.substring(1) : key) == false) {
			throw new IllegalArgumentException(method + "请求，字符 " + originKey + " 不合法！"
					+ " key:value 中的key只能关键词 '@key' 或 'key[逻辑符][条件符]' 或 PUT请求下的 'key+' / 'key-' ！");
		}

		if (saveLogic && last != null) {
			key = key + last;
		}
		Log.i(TAG, "getRealKey  return key = " + key);
		return key;
	}


	public interface Callback {
		AbstractSQLConfig getSQLConfig(RequestMethod method, String table);
	}

}
