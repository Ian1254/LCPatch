package com.lcpatch;

import static org.junit.Assert.assertEquals;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ScannerRuleTest {
    private static long u32(byte[] b, int p) {
        return ((long)b[p]&255)|(((long)b[p+1]&255)<<8)|(((long)b[p+2]&255)<<16)|(((long)b[p+3]&255)<<24);
    }
    private static boolean subSp(long v){return (v&0xffc003ffL)==0xd10003ffL;}
    private static boolean saveLr(long v){return (v&0xffc003ffL)==0xf90003feL||(v&0xffc07fffL)==0xa9007bfdL;}
    private static boolean ret(long v){return v==0xd65f03c0L;}
    private static boolean nullBranch(long v){return (v&0x7e000000L)==0x34000000L;}
    private static boolean length(long v){return (v&0xfffffc00L)==0xf9400800L;}
    private static boolean data(long v){return (v&0xfffffc00L)==0xf9400000L;}
    private static long target(int pc,long v){long imm=v&0x03ffffffL;if((imm&(1L<<25))!=0)imm-=1L<<26;return pc+(imm<<2);}

    @Test public void realUnityHasOneVerifiedCandidate() throws Exception {
        String configured=System.getProperty("lc.unity");
        Path path;
        if(configured!=null) path=Path.of(configured);
        else {
            Path cursor=Path.of(System.getProperty("user.dir")).toAbsolutePath(); path=null;
            while(cursor!=null&&path==null){Path candidate=cursor.resolve("lc_fontfix/libunity-1.113.1.so");if(Files.isRegularFile(candidate))path=candidate;else cursor=cursor.getParent();}
            if(path==null)throw new IllegalStateException("libunity-1.113.1.so fixture not found");
        }
        byte[] b=Files.readAllBytes(path);
        byte[] sig={0x08,0x1c,0x40,(byte)0xf9,0x00,0x01,0x02,(byte)0x91,(byte)0xc0,0x03,0x5f,(byte)0xd6};
        List<Integer> accessors=new ArrayList<>(),candidates=new ArrayList<>();
        outer:for(int p=0;p+12<=b.length;p+=4){for(int i=0;i<12;i++)if(b[p+i]!=sig[i])continue outer;accessors.add(p);}
        for(int p=0;p+60<=b.length;p+=4){long word=u32(b,p);if((word&0xfc000000L)!=0x94000000L||!accessors.contains((int)target(p,word)))continue;
            boolean len=false,dat=false,nul=false;for(int i=1;i<=14;i++){long x=u32(b,p+i*4);len|=length(x);dat|=data(x);nul|=nullBranch(x);}if(!(len&&dat&&nul))continue;
            boolean crossed=false;int floor=Math.max(0,p-768);for(int q=p-4;q>=floor;q-=4){long x=u32(b,q);if(ret(x))crossed=true;if(crossed||!subSp(x))continue;boolean saved=false;for(int k=q+4;k<Math.min(q+28,p);k+=4)saved|=saveLr(u32(b,k));if(saved){if(!candidates.contains(q))candidates.add(q);break;}}
        }
        // The executable PT_LOAD maps file offset 0x4000 at virtual address 0,
        // so raw-file 0xb811d0 is the runtime unity+0xb851d0 entry.
        assertEquals(List.of(0xb811d0),candidates);
    }
}
