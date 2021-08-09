# IO Benchmarks

```
Benchmark                                                            Mode  Cnt      Score     Error  Units
FileBenchmarks.testFilesReadChannel100                               avgt   10  12784.632 ± 367.794  ms/op
FileBenchmarks.testJvmRandomFileRead                                 avgt   10     30.879 ±   0.476  ms/op
FileBenchmarks.testJvmStreamRead                                     avgt   10     24.337 ±   0.586  ms/op
FileBenchmarks.testJvmStreamRead100                                  avgt   10   2480.255 ±  27.500  ms/op
FileBenchmarks.testKtorFakeFileRead                                  avgt   10    364.762 ±  25.866  ms/op
FileBenchmarks.testKtorFakeFileReadInHot                             avgt   10     73.430 ±   3.759  ms/op
FileBenchmarks.testKtorFileRead                                      avgt   10    368.891 ±  12.417  ms/op
FileBenchmarks.testKtorFileReadInBlocking2BlockingDispatcher         avgt   10    327.188 ±  25.345  ms/op
FileBenchmarks.testKtorFileReadInExp2ExpDispatcher                   avgt   10    285.341 ±  26.109  ms/op
FileBenchmarks.testKtorFileReadInFixed2FixedDispatcher               avgt   10    335.269 ±  26.333  ms/op
FileBenchmarks.testKtorFileReadInFixedDispatcher                     avgt   10    327.743 ±  17.517  ms/op
FileBenchmarks.testKtorFileReadInHot2HotDispatcher                   avgt   10     69.893 ±   2.384  ms/op
FileBenchmarks.testKtorFileReadInHotDispatcher                       avgt   10    189.024 ±   8.091  ms/op
FileBenchmarks.testKtorFileReadInHugeExp2HugeExpDispatcher           avgt   10    334.480 ±  24.396  ms/op
FileBenchmarks.testKtorFileReadInIODispatcher                        avgt   10     68.801 ±   1.771  ms/op
FileBenchmarks.testKtorFileReadInIo2IoDispatcher                     avgt   10     76.688 ±   4.480  ms/op
FileBenchmarks.testKtorFileReadUnconfined                            avgt   10     64.487 ±   1.205  ms/op
SocketBenchmarks.testJvmSocketWrite                                  avgt   10     41.340 ±   1.515  ms/op
SocketBenchmarks.testKtorSocketWrite                                 avgt   10    138.893 ±   2.345  ms/op
SocketBenchmarks.testKtorSocketWriteWithoutAutoFlush                 avgt   10    154.011 ±  11.539  ms/op
```