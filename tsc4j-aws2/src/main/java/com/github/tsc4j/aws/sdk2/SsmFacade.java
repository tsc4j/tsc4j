/*
 * Copyright 2017 - 2022 tsc4j project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.tsc4j.aws.sdk2;

import com.github.tsc4j.aws.common.AwsConfig;
import com.github.tsc4j.core.BaseInstance;
import com.github.tsc4j.core.Tsc4jException;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.github.tsc4j.core.utils.CollectionUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.DescribeParametersRequest;
import software.amazon.awssdk.services.ssm.model.DescribeParametersResponse;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.GetParametersRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterMetadata;
import software.amazon.awssdk.services.ssm.model.ParameterType;

import java.io.Closeable;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * Simple AWS SSM client facade.
 *
 * @see <a href="https://aws.amazon.com/systems-manager/">AWS Systems manager</a>
 */
@Slf4j
final class SsmFacade extends BaseInstance implements Closeable {
    /**
     * Maximum number of parameters in a single ssm request.
     *
     * @see <a href="https://docs.aws.amazon.com/systems-manager/latest/APIReference/API_GetParameters.html">AWS SSM
     *     Get parameters API Reference</a>
     */
    private static final int MAX_RESULTS = 10;

    /**
     * Max results requested in describe parameters request.
     *
     * @see <a href="https://docs.aws.amazon.com/systems-manager/latest/APIReference/API_DescribeParameters.html">AWS
     *     SSM Describe parameters API reference</a>
     */
    private static final int DESCRIBE_MAX_RESULTS = 50;

    /**
     * AWS SSM type.
     */
    static final String TYPE = "aws2.ssm";

    /**
     * Type aliases.
     */
    static final Set<String> TYPE_ALIASES = CollectionUtils.toImmutableSet(
        "aws2.param.store", "aws.param.store", "aws.ssm", "ssm");


    private static final Pattern SPLIT_PATTERN = Pattern.compile("\\s*,\\s*");

    private final SsmClient ssm;
    private final boolean decrypt;
    private final boolean parallel;

    /**
     * Creates new anonymous instance.
     *
     * @param awsInfo  aws info
     * @param decrypt  decrypt secure parameters?
     * @param parallel parallel parameter fetching?
     */
    SsmFacade(@NonNull AwsConfig awsInfo, boolean decrypt, boolean parallel) {
        this("", awsInfo, decrypt, parallel);
    }

    /**
     * Creates new named instance.
     *
     * @param awsInfo  aws info
     * @param decrypt  decrypt secure parameters?
     * @param parallel parallel parameter fetching?
     */
    SsmFacade(String name, @NonNull AwsConfig awsInfo, boolean decrypt, boolean parallel) {
        this(name, createSsmClient(awsInfo), decrypt, parallel);
    }

    /**
     * Creates new instance.
     *
     * @param ssm      ssm client
     * @param decrypt  decrypt secure parameters?
     * @param parallel parallel parameter fetching?
     */
    SsmFacade(String name, @NonNull SsmClient ssm, boolean decrypt, boolean parallel) {
        super(name);
        this.ssm = ssm;
        this.decrypt = decrypt;
        this.parallel = parallel;
    }

    /**
     * Creates AWS ssm client
     *
     * @param awsConfig aws info instance
     * @return aws ssm client
     */
    private static SsmClient createSsmClient(@NonNull AwsConfig awsConfig) {
        return AwsSdk2Utils.configuredClient(SsmClient::builder, awsConfig);
    }

    /**
     * Lists all AWS SSM parameters.
     *
     * @return list of discovered parameters.
     */
    List<ParameterMetadata> list() {
        val request = DescribeParametersRequest.builder()
            .maxResults(DESCRIBE_MAX_RESULTS)
            .build();
        return list(request);
    }

    /**
     * Lists all AWS SSM parameters that satisfy specified parameters request.
     *
     * @param request describe parameters request.
     * @return list of found parameters.
     */
    List<ParameterMetadata> list(@NonNull DescribeParametersRequest request) {
        val result = new ArrayList<ParameterMetadata>();

        String nextToken = null;
        do {
            val response = describeParameters(ssm, request, nextToken);
            log.debug("{} received description of {} parameter(s).", this, response.parameters().size());
            result.addAll(response.parameters());
            nextToken = response.nextToken();
        } while (nextToken != null && !nextToken.isEmpty());

        return result;
    }

    private DescribeParametersResponse describeParameters(@NonNull SsmClient ssm,
                                                          @NonNull DescribeParametersRequest request,
                                                          String nextToken) {
        val realReq = (nextToken == null) ? request : request.toBuilder()
            .nextToken(nextToken)
            .build();

        log.debug("{} describing aws ssm parameter store parameters: {}", this, realReq);
        try {
            return ssm.describeParameters(realReq);
        } catch (Exception e) {
            throw Tsc4jException.of("Error while describing AWS SSM parameters request %s: %%s",
                e, request.toString());
        }
    }

    /**
     * Converts parameter to config value.
     *
     * @param param parameter
     * @return config value
     */
    static ConfigValue toConfigValue(@NonNull Parameter param) {
        val type = param.type();

        log.trace("converting to ConfigValue: {}", param);
        val updated = Optional.ofNullable(param.lastModifiedDate())
            .map(it -> it.atZone(ZoneOffset.UTC).toString())
            .orElse("n/a");
        val originDescription = String.format("%s:%s, version: %d, modified: %s, arn: %s",
            TYPE, param.name(), param.version(), updated, param.arn());

        if (type == ParameterType.STRING_LIST) {
            val chunks = SPLIT_PATTERN.split(param.value());
            val list = Arrays.asList(chunks);
            return ConfigValueFactory.fromIterable(list, originDescription);
        }
        return ConfigValueFactory.fromAnyRef(param.value(), originDescription);
    }

    /**
     * Converts collection of parameters to config.
     *
     * @param params collection of parameters
     * @return config.
     */
    Config toConfig(@NonNull Collection<Parameter> params) {
        return params.stream()
            .reduce(ConfigFactory.empty(),
                (cfg, param) -> cfg.withValue(parameterTooConfigPath(param), parameterToConfigValue(param)),
                (previous, current) -> current);
    }

    private ConfigValue parameterToConfigValue(@NonNull Parameter parameter) {
        return toConfigValue(parameter);
    }

    private String parameterTooConfigPath(@NonNull Parameter parameter) {
        return parameter.name()
            .replace('/', '.')
            .replaceAll("^\\.*", "");
    }

    /**
     * Fetches parameters by one or more paths.
     *
     * @param paths parameters paths
     * @return list of fetched parameters.
     */
    List<Parameter> fetchByPath(String... paths) {
        return fetchByPath(Arrays.asList(paths));
    }

    /**
     * Fetches parameters by one or more paths.
     *
     * @param paths parameters paths
     * @return list of fetched parameters.
     */
    List<Parameter> fetchByPath(@NonNull Collection<String> paths) {
        val tasks = Tsc4jImplUtils.uniqStream(paths)
            .map(this::createGetParametersRequest)
            .map(this::toGetByPathTask)
            .collect(Collectors.toList());

        return runTasks(tasks, this.parallel).stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    }

    private GetParametersByPathRequest createGetParametersRequest(String path) {
        return GetParametersByPathRequest.builder()
            .path(path)
            .maxResults(MAX_RESULTS)
            .recursive(true)
            .withDecryption(decrypt)
            .build();
    }

    private Callable<List<Parameter>> toGetByPathTask(@NonNull GetParametersByPathRequest req) {
        return () -> {
            try {
                return getParametersByPath(req);
            } catch (Exception e) {
                throw Tsc4jException.of("Error fetching AWS SSM parameters by path %s: %%s", e, req.path());
            }
        };
    }

    private List<Parameter> getParametersByPath(GetParametersByPathRequest req) {
        val result = new ArrayList<Parameter>();

        String nextToken = null;
        do {
            val response = getParametersByPath(req, nextToken);
            log.debug("{} fetched {} parameter(s) for path: {}", this, response.parameters().size(), req.path());
            response.parameters().stream()
                .sorted(Comparator.comparing(Parameter::name))
                .forEach(result::add);
            nextToken = response.nextToken();
        } while (nextToken != null && !nextToken.isEmpty());

        return result;

    }

    private GetParametersByPathResponse getParametersByPath(@NonNull GetParametersByPathRequest req,
                                                            String nextToken) {
        val realReq = (nextToken == null)? req : req.toBuilder().nextToken(nextToken).build();
        return ssm.getParametersByPath(realReq);
    }

    /**
     * Fetches parameters.
     *
     * @param names parameter names
     * @return list of fetched parameters
     */
    List<Parameter> fetch(@NonNull List<String> names) {
        val requests = Tsc4jImplUtils.partitionList(names, MAX_RESULTS).stream()
            .map(e -> toGetParametersRequest(e, decrypt))
            .collect(Collectors.toList());

        val tasks = createFetchParametersTasks(ssm, requests);

        log.debug("{} created {} fetching tasks.", this, tasks.size());
        val results = runTasks(tasks, this.parallel);
        log.debug("{} retrieved {} get parameters results.", this, results.size());

        val invalidParams = results.stream()
            .flatMap(e -> e.invalidParameters().stream())
            .collect(Collectors.toList());
        if (!invalidParams.isEmpty()) {
            log.warn("{} invalid AWS SSM parameters: {}", this, invalidParams);
        }

        val params = results.stream()
            .flatMap(e -> e.parameters().stream())
            .collect(Collectors.toList());

        log.debug("{} retrieved {} AWS SSM parameters.", this, params.size());
        log.trace("{} retrieved AWS SSM parameters: {}", this, params);

        return params;
    }

    /**
     * Returns true AWS SSM parameter name.
     *
     * @param name parameter name
     * @return validated aws ssm parameter name
     * @see #ssmParamNameMap(Collection)
     */
    static String ssmParamName(@NonNull String name) {
        return (name.startsWith("/")) ? name : "/" + name;
    }

    /**
     * Creates map of AWS SSM parameter name => parameter name.
     *
     * @param names parameter names
     * @return map
     * @see #ssmParamName(String)
     */
    static Map<String, String> ssmParamNameMap(@NonNull Collection<String> names) {
        val res = CollectionUtils.<String, String>newMap();
        names.forEach(it -> res.put(ssmParamName(it), it));
        return res;
    }

    private List<Callable<GetParametersResponse>> createFetchParametersTasks(
        @NonNull SsmClient ssm,
        @NonNull Collection<GetParametersRequest> requests) {
        return requests.stream()
            .map(request -> createParameterFetchTask(ssm, request))
            .collect(Collectors.toList());
    }

    private Callable<GetParametersResponse> createParameterFetchTask(@NonNull SsmClient ssm,
                                                                     @NonNull GetParametersRequest request) {
        return () -> {
            try {
                return ssm.getParameters(request);
            } catch (Exception e) {
                throw Tsc4jException.of("Error fetching %d AWS SSM parameters: %%s", e, request.names().size());
            }
        };
    }

    private static GetParametersRequest toGetParametersRequest(@NonNull List<String> names, boolean decrypt) {
        if (names.isEmpty()) {
            throw new IllegalArgumentException("Parameter names cannot be empty.");
        }
        if (names.size() > MAX_RESULTS) {
            throw new IllegalArgumentException("Parameter names length cannot be > " + MAX_RESULTS);
        }

        return GetParametersRequest.builder()
            .names(names)
            .withDecryption(decrypt)
            .build();
    }

    @Override
    protected void doClose() {
        super.doClose();
        log.debug("{} closing ssm client: {}", this, ssm);
        ssm.close();
    }

    @Override
    public String getType() {
        return TYPE;
    }
}
