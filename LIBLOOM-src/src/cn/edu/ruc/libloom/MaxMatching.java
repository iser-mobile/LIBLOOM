package cn.edu.ruc.libloom;

/**
 * @author xuebo @date 2021/1/6
 * Bipartite graph maximum weight matching problem（Set X,Y） X:lib_class  Y:app_class
 *  Input：Weight adjacency matrix graph[x][y]
 *  Output：match result
 *      match[y] = x(x≠-1); （x,y）match，x∈X,y∈Y
 *      max_matching_pairs the number of match pairs
 */
public class MaxMatching {
    public int[] match;             //match record. Index refers to Y label, and value refers to the matched X label. e.g. match[1] = 2, label 1 in Y matchs with label 2 in X.
    public int max_matching_pairs;  //the number of match pairs when getting max matching
    public double avg_weight;       //average weight

    int[][] graph;         //Row-id refers to Set X label, and col-id refers to Y label. 
    int[] clazzmethods;    //methods count in lib pack
    int total;             //total methods count in lib
    boolean[] xUsed;      // flag that each X label is visited in each loop 
    boolean[] yUsed;      //flag that each Y label is visited in each loop
    int cardX,cardY;      //card(X) card(Y)
    int[] less;           
    private static final int PRECISION = 100;  // float2int for precision in max matching
    private static final int INFINITE = PRECISION+100;

    int[] X; // Set X label
    int[] Y; //Set Y label, initial with 0

    public MaxMatching(double[][] g,int[] methods){
        cardX = g.length;
        cardY = g[0].length;
        xUsed = new boolean[cardX];
        yUsed = new boolean[cardY];
        match = new int[cardY];
        max_matching_pairs = 0;
        graph = new int[cardX][cardY];
        clazzmethods = methods;

       
        for (int i = 0; i < cardX; i++){
            for (int j = 0; j < cardY; j++){
                graph[i][j] = (int) (Math.floor(g[i][j]*PRECISION));
            }
        }

        less = new int[cardY];
        X = new int[cardX];
        Y = new int[cardY];
       
        for(int j = 0; j < cardY; j ++){
            match[j] = -1;
            Y[j] = 0;
        }
       
        for(int i = 0; i < cardX; i ++){
            total += clazzmethods[i];  
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

    private boolean isAllZeros(double[][] g){ 
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
      
        for (int i = 0; i < cardX; i++) {
            for (int j = 0; j < cardY; j++) {
                less[j] = INFINITE;
            }
            long start = System.currentTimeMillis(), now;
            while (true) {  
                for (int j = 0; j < cardX; j++) {
                    xUsed[j] = false;
                }
                for (int j = 0; j < cardY; j++) {
                    yUsed[j] = false;
                }

                if ((!isExistingMatch(i)) || findAugmentPath(i)) {
                    break;  
                }

                int diff = INFINITE;        
                for (int j = 0; j < cardY; j++) {
                    if (!yUsed[j]) diff = diff <= less[j] ? diff : less[j];
                }
                
                boolean stopLoop = false;
                for (int j = 0; j < cardX; j++) {
                    if (xUsed[j]){
                        X[j] -= diff;
                        if(X[j] <= 0){       
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
                if((now-start) / 1000 > 5)  
                    break;
            }
        }

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

    }

    private boolean findAugmentPath(int i) {
        xUsed[i] = true;

        for (int j = 0; j < cardY; j++) {
            if (yUsed[j]) continue;                 
            int gap = X[i] + Y[j] - graph[i][j];    

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
        System.out.println("Generate adjacency matrix("+vxCount+","+vyCount+")...");
        double startTime = System.currentTimeMillis();
        double threshold = 0.9;  
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
        
        for(int i=0; i<vxCount; i++){
            for (int j=0; j<vyCount; j++){
                System.out.print(graph[i][j]+"\t");
            }
            System.out.println();
        }
        double endTime = System.currentTimeMillis();
        System.out.println("Generating adjacency matrix in "+(endTime-startTime)/1000+"s");
        return graph;
    }

//    public static void main(String[] args) {
//        new MaxMatching(randomGraph(50,60)).km();
//    }
}
