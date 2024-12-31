### I use this readme as a notepad for now



### To be investigated

`Long monitor contention with owner DefaultDispatcher-worker-9 (32596) at void android.graphics.pdf.PdfRenderer$Page.render(...) waiters=6 ... for 162ms`

This tells us that:
1. Six threads are waiting to access the renderer
2. They're waiting for significant periods (162ms to 1.924s)
3. The contention is happening primarily in two places:
   `PdfRenderer$Page.render()`
   `PdfRenderer$Page.<init>() (the page initialization)`

The root cause is that Android's PdfRenderer has internal synchronization that we didn't account for in our concurrent design.
Even though we're using different renderer instances, they're still experiencing contention.
This suggests that there might be some shared native resources that all PdfRenderer instances are competing for??