package com.mn.springboot.utils;

import com.alibaba.fastjson.serializer.SerializerFeature;
import org.neo4j.driver.internal.InternalNode;
import org.neo4j.driver.internal.InternalRelationship;
import org.neo4j.driver.v1.*;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Path;
import org.neo4j.driver.v1.types.Relationship;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.neo4j.driver.v1.util.Pair;
import com.alibaba.fastjson.JSON;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 通用的neo4j调用类
 *
 * @version 1.0 18-6-5 上午11:21
 */
@Component
public class Neo4jUtil {
    private static Driver driver;

    @Autowired
    public Neo4jUtil(Driver driver) {
        Neo4jUtil.driver = driver;
    }

    /**
     * cql的return返回多种节点match (n)-[edge]-(n) return n,m,edge：限定返回关系时，关系的别名必须“包含”edge
     * @param cql 查询语句
     * @param lists 和cql的return返回节点顺序对应
     * @return List<Map<String,Object>>
     */
    public static <T> void getList(String cql, Set<T>... lists) {
        System.out.println("===getList() 传入的参数cql====="+cql);
        //用于给每个Set list赋值
        int listIndex = 0;
        try {
            Session session = driver.session();
            StatementResult result = session.run(cql);
            List<Record> list = result.list();
            for (Record r : list) {
                if (r.size() != lists.length) {
                    System.out.println("节点数和lists数不匹配");
                    return;
                }
            }
            for (Record r : list) {
                for (String index : r.keys()) {
                    //对于关系的封装
                    if (index.indexOf("edge") != -1) {
                        Map<String, Object> map = new HashMap<>();
                        //关系上设置的属性
                        map.putAll(r.get(index).asMap());
                        //外加三个固定属性
                        map.put("edgeId", r.get(index).asRelationship().id());
                        map.put("edgeFrom", r.get(index).asRelationship().startNodeId());
                        map.put("edgeTo", r.get(index).asRelationship().endNodeId());
                        lists[listIndex++].add((T) map);
                    }
                    //对于节点的封装
                    else {
                        Map<String, Object> map = new HashMap<>();
                        //关系上设置的属性
                        map.putAll(r.get(index).asMap());
                        //外加一个固定属性
                        map.put("nodeId", r.get(index).asNode().id());
                        lists[listIndex++].add((T) map);
                    }
                }
                listIndex = 0;
            }
        } catch (Exception e) {
            System.out.println("=====getList() Exception e======");
            e.printStackTrace();
        }
    }

    /**
     * cql 路径查询 返回节点和关系
     * @param cql 查询语句
     * @param nodeList 节点
     * @param edgeList 关系
     * @return List<Map<String,Object>>
     */
    public static <T> void getPathList(String cql, Set<T> nodeList, Set<T> edgeList) {
        try {
            Session session = driver.session();
            StatementResult result = session.run(cql);
            List<Record> list = result.list();
            for (Record r : list) {
                for (String index : r.keys()) {
                    Path path = r.get(index).asPath();
                    //节点
                    Iterable<Node> nodes = path.nodes();
                    for (Iterator iter = nodes.iterator(); iter.hasNext(); ) {
                        InternalNode nodeInter = (InternalNode) iter.next();
                        Map<String, Object> map = new HashMap<>();
                        //节点上设置的属性
                        map.putAll(nodeInter.asMap());
                        //外加一个固定属性
                        map.put("nodeId", nodeInter.id());
                        nodeList.add((T) map);
                    }
                    //关系
                    Iterable<Relationship> edges = path.relationships();
                    for (Iterator iter = edges.iterator(); iter.hasNext(); ) {
                        InternalRelationship relationInter = (InternalRelationship) iter.next();
                        Map<String, Object> map = new HashMap<>();
                        map.putAll(relationInter.asMap());
                        //关系上设置的属性
                        map.put("edgeId", relationInter.id());
                        map.put("edgeFrom", relationInter.startNodeId());
                        map.put("edgeTo", relationInter.endNodeId());
                        edgeList.add((T) map);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * cql 返回具体的属性, 如match (n)-[]-() return n.id,n.name，match (n)-[]-() return count(n)
     * @param cql 查询语句
     * @return List<Map<String,Object>>
     */
    public static List<Map<String, Object>> getFields(String cql) {
        List<Map<String, Object>> resList = new ArrayList<>();
        try {
            Session session = driver.session();
            StatementResult result = session.run(cql);
            List<Record> list = result.list();
            for (Record r : list) {
                resList.add(r.asMap());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resList;
    }

    /**
     * 执行添加cql
     * @param cql 查询语句
     */
    public static void add(String cql) {
        //启动事务
        try (Session session = driver.session();
             Transaction tx = session.beginTransaction()) {
            tx.run(cql);
            //提交事务
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public StatementResult excuteCypherSql(String cypherSql) {
        StatementResult result = null;
        try (Session session = driver.session()) {
            System.out.println(cypherSql);
            result = session.run(cypherSql);
            System.out.println("====result===="+ result.toString());
            session.close();
        } catch (Exception e) {
            throw e;
        }
        return result;
    }

    public HashMap<String, Object> GetEntityMap(String cypherSql) {
        HashMap<String, Object> rss = new HashMap<String, Object>();
        try {
            StatementResult result = excuteCypherSql(cypherSql);
            if (result.hasNext()) {
                List<Record> records = result.list();
                for (Record recordItem : records) {
                    for (Value value : recordItem.values()) {
                        if (value.type().name().equals("NODE")) {// 结果里面只要类型为节点的值
                            Node noe4jNode = value.asNode();
                            Map<String, Object> map = noe4jNode.asMap();
                            for (Map.Entry<String, Object> entry : map.entrySet()) {
                                String key = entry.getKey();
                                if (rss.containsKey(key)) {
                                    String oldValue = rss.get(key).toString();
                                    String newValue = oldValue + "," + entry.getValue();
                                    rss.replace(key, newValue);
                                } else {
                                    rss.put(key, entry.getValue());
                                }
                            }

                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return rss;
    }

    public List<HashMap<String, Object>> GetGraphNode(String cypherSql) {
        List<HashMap<String, Object>> ents = new ArrayList<HashMap<String, Object>>();
        try {
            StatementResult result = excuteCypherSql(cypherSql);
            if (result.hasNext()) {
                List<Record> records = result.list();
                for (Record recordItem : records) {
                    List<Pair<String, Value>> f = recordItem.fields();
                    for (Pair<String, Value> pair : f) {
                        HashMap<String, Object> rss = new HashMap<String, Object>();
                        String typeName = pair.value().type().name();
                        if (typeName.equals("NODE")) {
                            Node noe4jNode = pair.value().asNode();
                            String uuid = String.valueOf(noe4jNode.id());
                            Map<String, Object> map = noe4jNode.asMap();
                            for (Map.Entry<String, Object> entry : map.entrySet()) {
                                String key = entry.getKey();
                                rss.put(key, entry.getValue());
                            }
                            rss.put("uuid", uuid);
                            ents.add(rss);
                        }
                    }

                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return ents;
    }

    public List<HashMap<String, Object>> GetGraphRelationShip(String cypherSql) {
        List<HashMap<String, Object>> ents = new ArrayList<HashMap<String, Object>>();
        try {
            StatementResult result = excuteCypherSql(cypherSql);
            if (result.hasNext()) {
                List<Record> records = result.list();
                for (Record recordItem : records) {
                    List<Pair<String, Value>> f = recordItem.fields();
                    for (Pair<String, Value> pair : f) {
                        HashMap<String, Object> rss = new HashMap<String, Object>();
                        String typeName = pair.value().type().name();
                        if (typeName.equals("RELATIONSHIP")) {
                            Relationship rship = pair.value().asRelationship();
                            String uuid = String.valueOf(rship.id());
                            String sourceid = String.valueOf(rship.startNodeId());
                            String targetid = String.valueOf(rship.endNodeId());
                            Map<String, Object> map = rship.asMap();
                            for (Map.Entry<String, Object> entry : map.entrySet()) {
                                String key = entry.getKey();
                                rss.put(key, entry.getValue());
                            }
                            rss.put("uuid", uuid);
                            rss.put("sourceid", sourceid);
                            rss.put("targetid", targetid);
                            ents.add(rss);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ents;
    }
    public List<HashMap<String, Object>> GetGraphItem(String cypherSql) {
        List<HashMap<String, Object>> ents = new ArrayList<HashMap<String, Object>>();
        List<String> nodeids = new ArrayList<String>();
        List<String> shipids = new ArrayList<String>();
        try {
            StatementResult result = excuteCypherSql(cypherSql);
            if (result.hasNext()) {
                List<Record> records = result.list();
                for (Record recordItem : records) {
                    List<Pair<String, Value>> f = recordItem.fields();
                    HashMap<String, Object> rss = new HashMap<String, Object>();
                    for (Pair<String, Value> pair : f) {
                        String typeName = pair.value().type().name();
                        if (typeName.equals("NODE")) {
                            Node noe4jNode = pair.value().asNode();
                            String uuid = String.valueOf(noe4jNode.id());
                            if(!nodeids.contains(uuid)) {
                                Map<String, Object> map = noe4jNode.asMap();
                                for (Map.Entry<String, Object> entry : map.entrySet()) {
                                    String key = entry.getKey();
                                    rss.put(key, entry.getValue());
                                }
                                rss.put("uuid", uuid);
                            }
                        }else if (typeName.equals("RELATIONSHIP")) {
                            Relationship rship = pair.value().asRelationship();
                            String uuid = String.valueOf(rship.id());
                            if (!shipids.contains(uuid)) {
                                String sourceid = String.valueOf(rship.startNodeId());
                                String targetid = String.valueOf(rship.endNodeId());
                                Map<String, Object> map = rship.asMap();
                                for (Map.Entry<String, Object> entry : map.entrySet()) {
                                    String key = entry.getKey();
                                    rss.put(key, entry.getValue());
                                }
                                rss.put("uuid", uuid);
                                rss.put("sourceid", sourceid);
                                rss.put("targetid", targetid);
                            }
                        }else {
                            rss.put(pair.key(),pair.value().toString());
                        }
                    }
                    ents.add(rss);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ents;
    }
    /*
     * 获取值类型的结果,如count,uuid
     * @return 1 2 3 等数字类型
     */
    public long GetGraphValue(String cypherSql) {
        long val=0;
        try {
            StatementResult cypherResult = excuteCypherSql(cypherSql);
            if (cypherResult.hasNext()) {
                Record record = cypherResult.next();
                for (Value value : record.values()) {
                    val = value.asLong();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return val;
    }

    /**
     *  执行cql语句
     *
     * @param cypherSql
     * @return
     */
    public HashMap<String, Object> GetGraphNodeAndShip(String cypherSql) {
        HashMap<String, Object> mo = new HashMap<String, Object>();
        /********************支持的cql类型如下****************************/
        cypherSql = "match p=()-[:like]-() return p limit 10";
        cypherSql = "MATCH p=()-[r:seen]->() RETURN p LIMIT 25";
        cypherSql = "start u1=node(9275),u2=node(9300) match p=(u1)-[:like*1..3]-(u2) return p";
        cypherSql = "match p=(n:user)-[]-() where n.name =\"aa\" return p";
        // cypherSql = "match (n:user) -[r]-(b:user) where n.name=\"aaa\" and b.name=\"ddd\" return *";
        // cypherSql = "match (n:user) -[r]-(b:user) where n.name=\"aaa\" and b.name=\"ddd\" return r";
        cypherSql = "MATCH p=()-[r:RE]->() RETURN p LIMIT 25";
        // cypherSql = "match(n:Movie)-[r:is]-(b) where n.title=\"英雄\" return *";
        // cypherSql = "start u=node(9302) match (u)-[r1:friend]->()-[r2:seen]->(m) return m";
        // cypherSql = "start u=node(9302) match path=(u)-[:friend]->(b)-[:seen]->(movie) where not (u)-[:seen]->(movie) return movie,path";
        // cypherSql = "match (n)-[r:like*1..5]-(b) where n.name=\"eee\" return *";
        // cypherSql = "match (n)-[r:like*1..2]-(b) where n.name=\"eee\" return *";
        // cypherSql = "match (n)-[r:RELATION]-(b) where n.entity_name=\"实体02\" return n,r,b";
        // cypherSql = "match (n)-[r:RELATION]-(b) where n.entity_name=\"实体02\" return n,r,b";
        // cypherSql = "MATCH p=()-[r:`法律依据`]->() RETURN p LIMIT 25";
        // cypherSql = "start n=node(1407325) match (n) return n";
        // cypherSql = "MATCH p=()-[r:`原审`]->() RETURN p LIMIT 25";
        // cypherSql = "start n=node(1409932) MATCH (n)-[r:原审]->(b) RETURN n,r,b";
        /************************************************/
        try {
            StatementResult result = excuteCypherSql(cypherSql);
            System.out.println("=========GetGraphNodeAndShip result=========="+ result.toString());
            if (result.hasNext()) {
                List<Record> records = result.list();
                List<HashMap<String, Object>> ents = new ArrayList<HashMap<String, Object>>();
                List<HashMap<String, Object>> ships = new ArrayList<HashMap<String, Object>>();
                List<String> uuids = new ArrayList<String>();
                List<String> shipids = new ArrayList<String>();
                for (Record recordItem : records) {
                    List<Pair<String, Value>> f = recordItem.fields();
                    for (Pair<String, Value> pair : f) {
                        HashMap<String, Object> rships = new HashMap<String, Object>();
                        HashMap<String, Object> rss = new HashMap<String, Object>();
                        String typeName = pair.value().type().name();
                        if (typeName.equals("NULL")) {
                            continue;
                        } else if (typeName.equals("NODE")) {
                            Node noe4jNode = pair.value().asNode();
                            Map<String, Object> map = noe4jNode.asMap();
                            String uuid = String.valueOf(noe4jNode.id());
                            if (!uuids.contains(uuid)) {
                                for (Map.Entry<String, Object> entry : map.entrySet()) {
                                    String key = entry.getKey();
                                    rss.put(key, entry.getValue());
                                }
                                rss.put("uuid", uuid);
                                rss.put("====111===labels",noe4jNode.labels());
                                uuids.add(uuid);
                            }
                            if (rss != null && !rss.isEmpty()) {
                                ents.add(rss);
                            }
                        } else if (typeName.equals("RELATIONSHIP")) {
                            Relationship rship = pair.value().asRelationship();
                            String uuid = String.valueOf(rship.id());
                            if (!shipids.contains(uuid)) {
                                String sourceid = String.valueOf(rship.startNodeId());
                                String targetid = String.valueOf(rship.endNodeId());
                                Map<String, Object> map = rship.asMap();
                                Map properties =  new HashMap<String,Object>();
                                for (Map.Entry<String, Object> entry : map.entrySet()) {
                                    String key = entry.getKey();
                                    // rships.put(key, entry.getValue());
                                    properties.put(key, entry.getValue());
                                }
                                rships.put("properties", properties);
                                rships.put("uuid", uuid);
                                rships.put("sourceid", sourceid);
                                rships.put("targetid", targetid);
                                rships.put("relation","****relation 2222***"+ rship.type());
                                shipids.add(uuid);
                                if (rships != null && !rships.isEmpty()) {
                                    ships.add(rships);
                                }
                            }

                        } else if (typeName.equals("PATH")) {
                            Path path = pair.value().asPath();
                            Map<String, Object> startNodemap = path.start().asMap();
                            String startNodeuuid = String.valueOf(path.start().id());
                            if (!uuids.contains(startNodeuuid)) {
                                rss=new HashMap<String, Object>();
                                for (Map.Entry<String, Object> entry : startNodemap.entrySet()) {
                                    String key = entry.getKey();
                                    rss.put(key, entry.getValue());
                                }
                                rss.put("uuid", startNodeuuid);
                                rss.put("====2222===labels",path.start().labels());
                                uuids.add(startNodeuuid);
                                if (rss != null && !rss.isEmpty()) {
                                    ents.add(rss);
                                }
                            }

                            Map<String, Object> endNodemap = path.end().asMap();
                            String endNodeuuid = String.valueOf(path.end().id());
                            if (!uuids.contains(endNodeuuid)) {
                                rss=new HashMap<String, Object>();
                                for (Map.Entry<String, Object> entry : endNodemap.entrySet()) {
                                    String key = entry.getKey();
                                    rss.put(key, entry.getValue());
                                }
                                rss.put("uuid", endNodeuuid);
                                rss.put("==444==labels", path.end().labels());
                                uuids.add(endNodeuuid);
                                if (rss != null && !rss.isEmpty()) {
                                    ents.add(rss);
                                }
                            }
                            Iterator<Node> allNodes = path.nodes().iterator();
                            while (allNodes.hasNext()) {
                                Node next = allNodes.next();
                                String uuid = String.valueOf(next.id());
                                if (!uuids.contains(uuid)) {
                                    rss=new HashMap<String, Object>();
                                    Map<String, Object> map = next.asMap();
                                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                                        String key = entry.getKey();
                                        rss.put(key, entry.getValue());
                                    }
                                    rss.put("uuid", uuid);
                                    rss.put("==333==labels",next.labels());
                                    uuids.add(uuid);
                                    if (rss != null && !rss.isEmpty()) {
                                        ents.add(rss);
                                    }
                                }
                            }
                            Iterator<Relationship> reships = path.relationships().iterator();
                            while (reships.hasNext()) {
                                Relationship next = reships.next();
                                String uuid = String.valueOf(next.id());
                                if (!shipids.contains(uuid)) {
                                    rships=new HashMap<String, Object>();
                                    String sourceid = String.valueOf(next.startNodeId());
                                    String targetid = String.valueOf(next.endNodeId());
                                    Map<String, Object> map = next.asMap();
                                    Map properties =  new HashMap<String,Object>();
                                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                                        String key = entry.getKey();
                                        // rships.put(key, entry.getValue());
                                        properties.put(key, entry.getValue());
                                    }
                                    rships.put("properties", properties);
                                    rships.put("uuid", uuid);
                                    rships.put("sourceid", sourceid);
                                    rships.put("targetid", targetid);
                                    rships.put("relation","****relation 1111***"+ next.type());
                                    shipids.add(uuid);
                                    if (rships != null && !rships.isEmpty()) {
                                        ships.add(rships);
                                    }
                                }
                            }
                        } else if (typeName.contains("LIST")) {
                            Iterable<Value> val=pair.value().values();
                            for(Iterator it = val.iterator(); it.hasNext(); ){
                                Value next = (Value)it.next();
                                String type=next.type().name();
                                    if (type.equals("RELATIONSHIP")) {
                                        Relationship rship = next.asRelationship();
                                        String uuid = String.valueOf(rship.id());
                                        if (!shipids.contains(uuid)) {
                                            System.out.println("=======uuid======"+uuid);
                                            String sourceid = String.valueOf(rship.startNodeId());
                                            String targetid = String.valueOf(rship.endNodeId());
                                            Map<String, Object> map = rship.asMap();
                                            Map properties =  new HashMap<String,Object>();
                                            for (Map.Entry<String, Object> entry : map.entrySet()) {
                                                String key = entry.getKey();
                                                // rships.put(key, entry.getValue());
                                                properties.put(key, entry.getValue());
                                            }

                                            rships.put("properties", properties);
                                            rships.put("uuid", uuid);
                                            rships.put("sourceid", sourceid);
                                            rships.put("targetid", targetid);
                                            rships.put("relation","****relation 333***"+ rship.type());
                                            shipids.add(uuid);
                                            if (rships != null && !rships.isEmpty()) {
                                                ships.add(rships);
                                            }
                                        }
                                    }
                            }
                        } else if (typeName.contains("MAP")) {
                            rss.put(pair.key(), pair.value().asMap());
                        } else {
                            rss.put(pair.key(), pair.value().toString());
                            if (rss != null && !rss.isEmpty()) {
                                ents.add(rss);
                            }
                        }

                    }
                }
                mo.put("node", ents);
                mo.put("relationship", ships);
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
        return mo;
    }
    /**
     * 匹配所有类型的节点,可以是节点,关系,数值,路径
     * @param cypherSql
     * @return
     */
    public List<HashMap<String, Object>> GetEntityList(String cypherSql) {
        List<HashMap<String, Object>> ents = new ArrayList<HashMap<String, Object>>();
        try {
            StatementResult result = excuteCypherSql(cypherSql);
            if (result.hasNext()) {
                List<Record> records = result.list();
                for (Record recordItem : records) {
                    HashMap<String, Object> rss = new HashMap<String, Object>();
                    List<Pair<String, Value>> f = recordItem.fields();
                    for (Pair<String, Value> pair : f) {
                        String typeName = pair.value().type().name();
                        if (typeName.equals("NULL")) {
                            continue;
                        } else if (typeName.equals("NODE")) {
                            Node noe4jNode = pair.value().asNode();
                            Map<String, Object> map = noe4jNode.asMap();
                            for (Map.Entry<String, Object> entry : map.entrySet()) {
                                String key = entry.getKey();
                                rss.put(key, entry.getValue());
                            }
                        } else if (typeName.equals("RELATIONSHIP")) {
                            Relationship rship = pair.value().asRelationship();
                            Map<String, Object> map = rship.asMap();
                            for (Map.Entry<String, Object> entry : map.entrySet()) {
                                String key = entry.getKey();
                                rss.put(key, entry.getValue());
                            }
                        } else if (typeName.equals("PATH")) {

                        } else if (typeName.contains("LIST")) {
                            rss.put(pair.key(), pair.value().asList());
                        } else if (typeName.contains("MAP")) {
                            rss.put(pair.key(), pair.value().asMap());
                        } else {
                            rss.put(pair.key(), pair.value().toString());
                        }
                    }
                    ents.add(rss);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return ents;
    }

    public <T> List<T> GetEntityItemList(String cypherSql, Class<T> type) {
        List<HashMap<String, Object>> ents=GetGraphNode(cypherSql);
        List<T> model = HashMapToObject(ents, type);
        return model;
    }

    public <T> T GetEntityItem(String cypherSql, Class<T> type) {
        HashMap<String, Object> rss = new HashMap<String, Object>();
        try {
            StatementResult result = excuteCypherSql(cypherSql);
            if (result.hasNext()) {
                Record record = result.next();
                for (Value value : record.values()) {
                    if (value.type().name().equals("NODE")) {// 结果里面只要类型为节点的值
                        Node noe4jNode = value.asNode();
                        Map<String, Object> map = noe4jNode.asMap();
                        for (Map.Entry<String, Object> entry : map.entrySet()) {
                            String key = entry.getKey();
                            if (rss.containsKey(key)) {
                                String oldValue = rss.get(key).toString();
                                String newValue = oldValue + "," + entry.getValue();
                                rss.replace(key, newValue);
                            } else {
                                rss.put(key, entry.getValue());
                            }
                        }

                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        T model = HashMapToObjectItem(rss, type);
        return model;
    }

    public HashMap<String, Object> GetEntity(String cypherSql) {
        HashMap<String, Object> rss = new HashMap<String, Object>();
        try {
            StatementResult result = excuteCypherSql(cypherSql);
            if (result.hasNext()) {
                Record record = result.next();
                for (Value value : record.values()) {
                    String t = value.type().name();
                    if (value.type().name().equals("NODE")) {// 结果里面只要类型为节点的值
                        Node noe4jNode = value.asNode();
                        Map<String, Object> map = noe4jNode.asMap();
                        for (Map.Entry<String, Object> entry : map.entrySet()) {
                            String key = entry.getKey();
                            if (rss.containsKey(key)) {
                                String oldValue = rss.get(key).toString();
                                String newValue = oldValue + "," + entry.getValue();
                                rss.replace(key, newValue);
                            } else {
                                rss.put(key, entry.getValue());
                            }
                        }

                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return rss;
    }

    public Integer executeScalar(String cypherSql) {
        Integer count = 0;
        try {
            StatementResult result = excuteCypherSql(cypherSql);
            if (result.hasNext()) {
                Record record = result.next();
                for (Value value : record.values()) {
                    String t = value.type().name();
                    if (t.equals("INTEGER")) {
                        count = Integer.valueOf(value.toString());
                        break;
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return count;
    }

    public HashMap<String, Object> GetRelevantEntity(String cypherSql) {
        HashMap<String, Object> rss = new HashMap<String, Object>();
        try {
            StatementResult resultNode = excuteCypherSql(cypherSql);
            if (resultNode.hasNext()) {
                List<Record> records = resultNode.list();
                for (Record recordItem : records) {
                    Map<String, Object> r = recordItem.asMap();
                    System.out.println(JSON.toJSONString(r));
                    String key = r.get("key").toString();
                    if (rss.containsKey(key)) {
                        String oldValue = rss.get(key).toString();
                        String newValue = oldValue + "," + r.get("value");
                        rss.replace(key, newValue);
                    } else {
                        rss.put(key, r.get("value"));
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return rss;
    }



    public String getFilterPropertiesJson(String jsonStr) {
        String propertiesString = jsonStr.replaceAll("\"(\\w+)\"(\\s*:\\s*)", "$1$2"); // 去掉key的引号
        return propertiesString;
    }
    public <T>String getkeyvalCyphersql(T obj) {
        Map<String, Object> map = new HashMap<String, Object>();
        List<String> sqlList=new ArrayList<String>();
        // 得到类对象
        Class userCla = obj.getClass();
        /* 得到类中的所有属性集合 */
        Field[] fs = userCla.getDeclaredFields();
        for (int i = 0; i < fs.length; i++) {
            Field f = fs[i];
            Class type = f.getType();

            f.setAccessible(true); // 设置些属性是可以访问的
            Object val = new Object();
            try {
                val = f.get(obj);
                if(val==null) {
                    val="";
                }
                String sql="";
                String key=f.getName();
                System.out.println("key:"+key+"type:"+type);
                if ( val instanceof   Integer ){
                    // 得到此属性的值
                    map.put(key, val);// 设置键值
                    sql="n."+key+"="+val;
                }
                else if ( val instanceof   String[] ){
                    //如果为true则强转成String数组
                    String [] arr = ( String[] ) val ;
                    String v="";
                    for ( int j = 0 ; j < arr.length ; j++ ){
                        arr[j]="'"+ arr[j]+"'";
                    }
                    v=String.join(",", arr);
                    sql="n."+key+"=["+val+"]";
                }
                else if (val instanceof List){
                    //如果为true则强转成String数组
                    List<String> arr = ( ArrayList<String> ) val ;
                    List<String> aa=new ArrayList<String>();
                    String v="";
                    for (String s : arr) {
                        s="'"+ s+"'";
                        aa.add(s);
                    }
                    v=String.join(",", aa);
                    sql="n."+key+"=["+v+"]";
                }
                else {
                    // 得到此属性的值
                    map.put(key, val);// 设置键值
                    sql="n."+key+"='"+val+"'";
                }

                sqlList.add(sql);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        String finasql=String.join(",",sqlList);
        System.out.println("单个对象的所有键值==反射==" + map.toString());
        return finasql;
    }
    public <T> List<T> HashMapToObject(List<HashMap<String, Object>> maps, Class<T> type) {
        try {
            List<T> list = new ArrayList<T>();
            for (HashMap<String, Object> r : maps) {
                T t = type.newInstance();
                Iterator iter = r.entrySet().iterator();// 该方法获取列名.获取一系列字段名称.例如name,age...
                while (iter.hasNext()) {
                    Map.Entry entry = (Map.Entry) iter.next();// 把hashmap转成Iterator再迭代到entry
                    String key = entry.getKey().toString(); // 从iterator遍历获取key
                    Object value = entry.getValue(); // 从hashmap遍历获取value
                    if("serialVersionUID".toLowerCase().equals(key.toLowerCase()))continue;
                    Field field = type.getDeclaredField(key);// 获取field对象
                    if (field != null) {
                        field.setAccessible(true);
                        if (field.getType() == int.class || field.getType() == Integer.class) {
                            if (value==null||StringUtil.isBlank(value.toString())) {
                                field.set(t, 0);// 设置值
                            } else {
                                field.set(t, Integer.parseInt(value.toString()));// 设置值
                            }
                        }
                        else if (field.getType() == long.class||field.getType() == Long.class ) {
                            if (value==null||StringUtil.isBlank(value.toString())) {
                                field.set(t, 0);// 设置值
                            } else {
                                field.set(t, Long.parseLong(value.toString()));// 设置值
                            }

                        }
                        else {
                            field.set(t, value);// 设置值
                        }
                    }

                }
                list.add(t);
            }

            return list;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T HashMapToObjectItem(HashMap<String, Object> map, Class<T> type) {
        try {
            T t = type.newInstance();
            Iterator iter = map.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry entry = (Map.Entry) iter.next();// 把hashmap转成Iterator再迭代到entry
                String key = entry.getKey().toString(); // 从iterator遍历获取key
                Object value = entry.getValue(); // 从hashmap遍历获取value
                if("serialVersionUID".toLowerCase().equals(key.toLowerCase()))continue;
                Field field = type.getDeclaredField(key);// 获取field对象
                if (field != null) {
                    field.setAccessible(true);
                    if (field.getType() == int.class || field.getType() == Integer.class) {
                        if (value==null||StringUtil.isBlank(value.toString())) {
                            field.set(t, 0);// 设置值
                        } else {
                            field.set(t, Integer.parseInt(value.toString()));// 设置值
                        }
                    }
                    else if (field.getType() == long.class||field.getType() == Long.class ) {
                        if (value==null||StringUtil.isBlank(value.toString())) {
                            field.set(t, 0);// 设置值
                        } else {
                            field.set(t, Long.parseLong(value.toString()));// 设置值
                        }

                    }
                    else {
                        field.set(t, value);// 设置值
                    }
                }

            }

            return t;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
