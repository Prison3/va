aux_source_directory(. DIR_LIB_SRCS)

# 生成链接库
ADD_LIBRARY(bitmap SHARED ${DIR_LIB_SRCS})

# 导入当前目录下的所有头文件
include_directories(.)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(
        bitmap
        ${log-lib}
        ${jnigraphics-lib}
)