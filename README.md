越来越多第三方库使用apt技术，如DBflow、Dagger2、ButterKnife等。在编译时根据Annotation生成了相关的代码，给开发带来了很大的便利。感觉APT这项技术很炫酷，所以就有了这篇文章。

## Annotation
如果想学习APT，那么就必须先了解Annotation的基础，这里附加我另外一篇文章的地址：
## APT
APT(Annotation Processing Tool)是一种处理注释的工具,它对源代码文件进行检测找出其中的Annotation，使用Annotation进行额外的处理。
      Annotation处理器在处理Annotation时可以根据源文件中的Annotation生成额外的源文件和其它的文件(文件具体内容由Annotation处理器的编写者决定),APT还会编译生成的源文件和原来的源文件，将它们一起生成class文件。

### 创建Annotation Module
首先，我们需要新建一个名称为annotation的Java Library，主要放置一些项目中需要使用到的Annotation和关联代码。这里简单自定义了一个注解：
```
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS) 
public @interface Test {   } 
```
##### 配置build.gradle，主要是规定jdk版本

```
apply plugin: 'java'
sourceCompatibility = 1.7 
targetCompatibility = 1.7 
dependencies {
    compile fileTree(dir: 'libs', include: ['*.jar'])
}
```



### 创建Compiler Module
创建一个名为compiler的Java Library，这个类将会写代码生成的相关代码。核心就是在这里。
##### 配置build.gradle

```
apply plugin: 'java'
sourceCompatibility = 1.7 
targetCompatibility = 1.7 
dependencies {
    compile fileTree(dir: 'libs', include: ['*.jar'])
    compile 'com.google.auto.service:auto-service:1.0-rc2'
    compile 'com.squareup:javapoet:1.7.0'
    compile project(':annotation')
}
```
1. 定义编译的jdk版本为1.7，这个很重要，不写会报错。
2. AutoService 主要的作用是注解 processor 类，并对其生成 META-INF 的配置信息。
3. JavaPoet 这个库的主要作用就是帮助我们通过类调用的形式来生成代码。
4. 依赖上面创建的annotation Module。
#### 定义Processor类
生成代码相关的逻辑就放在这里。

```
@AutoService(Processor.class)
public class TestProcessor extends AbstractProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(Test.class.getCanonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return false;
    }
}
```
##### 生成第一个类
我们接下来要生成下面这个HelloWorld的代码：
```
package com.example.helloworld;

public final class HelloWorld {
  public static void main(String[] args) {
    System.out.println("Hello, JavaPoet!");
  }
}
```
修改上述TestProcessor的process方法

```
@Override    
public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
   MethodSpec main = MethodSpec.methodBuilder("main")
           .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
           .returns(void.class)
           .addParameter(String[].class, "args")
           .addStatement("$T.out.println($S)", System.class, "Hello, JavaPoet!")
           .build();
   TypeSpec helloWorld = TypeSpec.classBuilder("HelloWorld")
           .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
           .addMethod(main)
           .build();
   JavaFile javaFile = JavaFile.builder("com.example.helloworld", helloWorld)
           .build();
   try {
       javaFile.writeTo(processingEnv.getFiler());
   } catch (IOException e) {
       e.printStackTrace();
   }
   return false;
}

```

### 在app中使用
##### 配置项目根目录的build.gradle

```
dependencies {
    classpath 'com.neenbedankt.gradle.plugins:android-apt:1.8'
}
```


##### 配置app的build.gradle

```
apply plugin: 'com.android.application'
apply plugin: 'com.neenbedankt.android-apt'
//...
dependencies {
    //..
    compile project(':annotation')
    apt project(':compiler')
}
```
##### 编译使用
在随意一个类添加@Test注解
```
@Test
public class MainActivity extends AppCompatActivity {

   @Override
   protected void onCreate(Bundle savedInstanceState) {
       super.onCreate(savedInstanceState);
       setContentView(R.layout.activity_main);
   }
}

```

点击Android Studio的ReBuild Project，可以在在app的 `build/generated/source/apt`目录下，即可看到生成的代码。

### 基于注解的View注入：DIActivity
到目前我们还没有使用注解，上面的@Test也没有实际用上，下面我们做一些更加实际的代码生成。实现基于注解的View，代替项目中的`findByView`。这里仅仅是学习怎么用APT，如果真的想用DI框架，推荐使用ButterKnife，功能全面。
1. 第一步，在annotation module创建@DIActivity、@DIView注解。

```
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface DIActivity {
    
}
```

```
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DIView {
    int value() default 0;
}
```
2. 创建DIProcessor方法

```
@AutoService(Processor.class)
public class DIProcessor extends AbstractProcessor {

    private Elements elementUtils;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        // 规定需要处理的注解
        return Collections.singleton(DIActivity.class.getCanonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        System.out.println("DIProcessor");
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(DIActivity.class);
        for (Element element : elements) {
            // 判断是否Class
            TypeElement typeElement = (TypeElement) element;
            List<? extends Element> members = elementUtils.getAllMembers(typeElement);
            MethodSpec.Builder bindViewMethodSpecBuilder = MethodSpec.methodBuilder("bindView")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(TypeName.VOID)
                    .addParameter(ClassName.get(typeElement.asType()), "activity");
            for (Element item : members) {
                DIView diView = item.getAnnotation(DIView.class);
                if (diView == null){
                    continue;
                }
                bindViewMethodSpecBuilder.addStatement(String.format("activity.%s = (%s) activity.findViewById(%s)",item.getSimpleName(),ClassName.get(item.asType()).toString(),diView.value()));
            }
            TypeSpec typeSpec = TypeSpec.classBuilder("DI" + element.getSimpleName())
                    .superclass(TypeName.get(typeElement.asType()))
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .addMethod(bindViewMethodSpecBuilder.build())
                    .build();
            JavaFile javaFile = JavaFile.builder(getPackageName(typeElement), typeSpec).build();

            try {
                javaFile.writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return true;
    }

    private String getPackageName(TypeElement type) {
        return elementUtils.getPackageOf(type).getQualifiedName().toString();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        elementUtils = processingEnv.getElementUtils();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_7;
    }
}
```
3. 使用DIActivity

```
@DIActivity
public class MainActivity extends Activity {
    @DIView(R.id.text)
    TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        DIMainActivity.bindView(this);
        textView.setText("Hello World!");
    }
}
```
实际上就是通过apt生成以下代码
```
public final class DIMainActivity extends MainActivity {
 public static void bindView(MainActivity activity) {
   activity.textView = (android.widget.TextView) activity.findViewById(R.id.text);
 }
}
```
运行看效果

### 常用的类总结
1. TypeElement
> 获取方式
2. ExecutableElement
3. TypeName
4. Type
5. ClassName
> 获取方式
6. Modifier
7. 泛型参数创建
> ParameterizedTypeName.get(ClassName.get(Map.class), String.class,String.class)
8. 


### 总结
上面Example的代码放在github：https://github.com/taoweiji/DemoAPT。也推荐阅读dagger2、dbflow、ButterKnife等基于apt的开源项目代码。
JavaPoet：https://github.com/square/javapoet 也有很多例子可以学习。
### 项目推荐：AptPreferences（快速持久化框架）
本人基于APT技术做了一个AptPrefences项目，用于更加方便地使用SharedPreferences，通过对普通的javabean类增加注解，即可把该类变成了SharedPreferences的封装类，降低使用SharedPreferences的成本，也让代码更加规范。
项目地址 https://github.com/taoweiji/AptPreferences
