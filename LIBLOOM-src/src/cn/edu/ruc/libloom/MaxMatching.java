package cn.edu.ruc.libloom;

/**
 * @author xuebo @date 2021/1/6
 * 二部图最大权匹配问题（集合X,Y） X:lib_class  Y:app_class
 *  输入：带权值的邻接矩阵graph[x][y]
 *  输出：最大权匹配结果
 *      match[y] = x(x≠-1); （x,y）配对，x∈X,y∈Y
 *      max_matching_pairs 最大权值匹配时配对数
 */
public class MaxMatching {
    public int[] match;             //每个Y顶点匹配的X顶点，即第i个Y顶点匹配的是第match[i]个X顶点
    public int max_matching_pairs;  //获得最大匹配时配对数
    public double avg_weight;       //平均权重

    int[][] graph;         //假设graph的行是顶点X集合（其中的顶点简称X顶点），列是顶点Y集合（其中的顶点简称Y顶点）
    int[] clazzmethods;    //lib pack中method数量
    int total;             //统计lib中method总数
    boolean[] xUsed;      //在每次循环中每个X顶点是否访问过
    boolean[] yUsed;      //在每次循环中每个Y顶点是否访问过
    int cardX,cardY;      //图的大小为cardX*cardY
    int[] less;           //与顶标变化相关
    private static final int PRECISION = 100;  //小数处理精度
    private static final int INFINITE = PRECISION+100;

    int[] X; //每个X顶点的顶标
    int[] Y; //每个Y顶点的顶标，初始化为0

    public MaxMatching(double[][] g,int[] methods){
        cardX = g.length;
        cardY = g[0].length;
        xUsed = new boolean[cardX];
        yUsed = new boolean[cardY];
        match = new int[cardY];
        max_matching_pairs = 0;
        graph = new int[cardX][cardY];
        clazzmethods = methods;

        //将小数处理为整数，方便KM算法处理，小数精度运算容易产生bug
        for (int i = 0; i < cardX; i++){
            for (int j = 0; j < cardY; j++){
                graph[i][j] = (int) (Math.floor(g[i][j]*PRECISION));
            }
        }

        less = new int[cardY];
        X = new int[cardX];
        Y = new int[cardY];
        //初始化配对结果数组
        for(int j = 0; j < cardY; j ++){
            match[j] = -1;
            Y[j] = 0;
        }
        //初始化顶标值
        for(int i = 0; i < cardX; i ++){
            total += clazzmethods[i];   //顺带统计lib_class中method数量
            X[i] = graph[i][0];
            for(int j = 1; j < cardY; j++){
                X[i] = X[i] > graph[i][j] ? X[i] : graph[i][j];
            }
        }

        if(!isAllZeros(g)){
            km();
        } else{
            max_matching_pairs = 0;
            avg_weight = 0.0;
        }
    }

    private boolean isAllZeros(double[][] g){ //判断是否为全0矩阵
        for(int i = 0; i < g.length; i++){
            for (int j = 0; j< g[0].length; j++){
                if(g[i][j] != 0)
                    return false;
            }
        }
        return true;
    }

    private boolean isExistingMatch(int x_index){
        for(int j = 0; j < graph[x_index].length; j++){
            if(graph[x_index][j] != 0)
                return true;
        }
        return false;
    }

    void km() {
        //遍历每个X顶点
        for (int i = 0; i < cardX; i++) {
            for (int j = 0; j < cardY; j++) {
                less[j] = INFINITE;
            }
            long start = System.currentTimeMillis(), now;
            while (true) {   //寻找能与X顶点匹配的Y顶点，如果找不到就降低X顶点的顶标继续寻找
                for (int j = 0; j < cardX; j++) {
                    xUsed[j] = false;
                }
                for (int j = 0; j < cardY; j++) {
                    yUsed[j] = false;
                }

                if ((!isExistingMatch(i)) || findAugmentPath(i)) {
                    break;  //寻找到匹配的Y顶点，退出 若权值为double,此处处理可能会陷入死循环。
                }

                //如果没有找到能够匹配的Y顶点，则降低X顶点的顶标，提升Y顶点的顶标，再次循环
                int diff = INFINITE;        //diff是顶标变化的数值
                for (int j = 0; j < cardY; j++) {
                    if (!yUsed[j]) diff = diff <= less[j] ? diff : less[j];
                }
                //diff等于为了使该顶点X能够匹配到一个Y顶点，其X的顶标所需要降低的最小值

                //更新顶标
                boolean stopLoop = false;
                for (int j = 0; j < cardX; j++) {
                    if (xUsed[j]){
                        X[j] -= diff;
                        if(X[j] <= 0){       //一旦顶标为负，停止循环
                            stopLoop = true;
                        }
                    }
                }
                if(stopLoop){
                    break;
                }

                for (int j = 0; j < cardY; j++) {
                    if (yUsed[j])
                        Y[j] += diff;
                    else
                        less[j] -= diff;
                }

                now = System.currentTimeMillis();
                if((now-start) / 1000 > 5)  //二分图没有完备匹配时会陷入死循环
                    break;
            }
        }

        //匹配完成，可以输出结果
        double res = 0;

        for (int i = 0; i < cardY; i++) {
            if(match[i] != -1 && graph[match[i]][i] != 0){
                max_matching_pairs ++;
                //avg_weight += (1.0 * graph[match[i]][i]) / PRECISION * (1.0 * clazzmethods[match[i]] / total);
                avg_weight += (1.0 * graph[match[i]][i]) / PRECISION;

            }
        }
        if(max_matching_pairs == 0){
            avg_weight = 0.0f;
        } else {
            avg_weight /= max_matching_pairs;
        }

//        System.out.println("最大匹配数" + max_matching_pairs + "\t最大权值：" + res);
//        System.out.println("平均权值"+avg_weight);
    }

    // 寻找集合X中i的增广路径
    private boolean findAugmentPath(int i) {
        //设置这个X顶点在此轮循环中被访问过
        xUsed[i] = true;

        //对于这个X顶点，遍历每个Y顶点
        for (int j = 0; j < cardY; j++) {
            if (yUsed[j]) continue;                 //每轮循环中每个Y顶点只访问一次
            int gap = X[i] + Y[j] - graph[i][j];    //KM算法的顶标变化公式

            //只有X顶点的顶标加上Y顶点的顶标等于graph中它们之间的边的权时才能匹配成功
            if (gap == 0) {
                yUsed[j] = true;
                if (match[j] == -1 || findAugmentPath(match[j])) {
                    match[j] = i;
                    return true;
                }
            } else {
                less[j] = less[j] <= gap ? less[j] : gap;
            }
        }
        return false;
    }

    // test
    public static double[][] randomGraph(int vxCount, int vyCount){
        System.out.println("邻接矩阵("+vxCount+","+vyCount+")生成中...");
        double startTime = System.currentTimeMillis();
        double threshold = 0.9;  //邻接矩阵稀疏程度，值越大越稀疏（包含0越多）
        double [][]graph = new double[vxCount][vyCount];
        for(int i=0; i<vxCount; i++){
            for (int j=0; j<vyCount; j++){
                if(Math.random() < threshold){
                    graph[i][j] = 0;
                } else {
                    graph[i][j] = Math.random();
                }
            }
        }
        //打印邻接矩阵
        for(int i=0; i<vxCount; i++){
            for (int j=0; j<vyCount; j++){
                System.out.print(graph[i][j]+"\t");
            }
            System.out.println();
        }
        double endTime = System.currentTimeMillis();
        System.out.println("邻接矩阵生成用时"+(endTime-startTime)/1000+"s");
        return graph;
    }

//    public static void main(String[] args) {
//        new MaxMatching(randomGraph(50,60)).km();
//    }
}
