public class CommonInfo
{
    private int numberOfPreferredNeighbors;
    private int unchokingInterval;
    private int optimisticUnchokingInterval;
    private String fileName;
    private int fileSize;
    private int pieceSize;

    //setting no of preferred neighbors
    public void setNumberOfPreferredNeighbors(int k){
        numberOfPreferredNeighbors = k;
    }

    //getting no of preferred neighbors
    public int getNumberOfPreferredNeighbors(){
        return numberOfPreferredNeighbors;
    }

    //setting unchokingInterval
    public void setUnchokingInterval(int u){
        unchokingInterval = u;
    }

    //getting unchokingInterval
    public int getUnchokingInterval(){
        return unchokingInterval;
    }
    //setting optimisticUnchokingInterval
    public void setOptimisticUnchokingInterval(int o){
        optimisticUnchokingInterval = o;
    }

    //getting optimisticUnchokingInterval
    public int getOptimisticUnchokingInterval(){
        return optimisticUnchokingInterval;
    }

    //setting filename
    public void setFileName(String f){
        fileName = f;
    }

    //getting fileName
    public String getFileName(){
        return fileName;
    }
    //setting fileSize
    public void setFileSize(int size){
        fileSize = size;
    }

    //getFileSize
    public int getFileSize(){
        return fileSize;
    }

    //setting pieceSize
    public void setPieceSize(int psize){
        pieceSize = psize;
    }
    //getting pieceSize
    public int getPieceSize(){
        return pieceSize;
    }
}