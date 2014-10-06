/*
 * Copyright (c) 2013-2014, Centre for Genomic Regulation (CRG).
 * Copyright (c) 2013-2014, Paolo Di Tommaso and the respective authors.
 *
 *   This file is part of 'Nextflow'.
 *
 *   Nextflow is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Nextflow is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Nextflow.  If not, see <http://www.gnu.org/licenses/>.
 */

package nextflow.executor
import java.nio.file.Files
import java.nio.file.Paths

import nextflow.processor.TaskConfig
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.script.BaseScript
import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class LsfExecutorTest extends Specification {


    def 'test bsub cmd line' () {

        setup:
        def folder = Files.createTempDirectory('test')
        // mock process
        def proc = Mock(TaskProcessor)
        def base = Mock(BaseScript)
        def config = new TaskConfig(base)
        // LSF executor
        def executor = [:] as LsfExecutor
        executor.taskConfig = config

        when:
        // process name
        proc.getName() >> 'task'
        // the script
        def script = folder.resolve('job.sh'); script.text = 'some content'
        // config
        config.queue = 'hpc-queue1'
        config.maxMemory = '2GB'
        config.maxDuration = '3h'
        config.clusterOptions = " -M 4000  -R 'rusage[mem=4000] select[mem>4000]' --X \"abc\" "
        config.cpu = test_cpu
        config.penv = test_penv
        // task object
        def task = new TaskRun()
        task.processor = proc
        task.workDir = Paths.get('/xxx')
        task.index = 1

        then:
        executor.getSubmitCommandLine(task, script) == expected
        script.canExecute()

        cleanup:
        folder?.deleteDir()

        where:
        test_cpu | test_penv | expected
        null | null | ['bsub','-cwd','/xxx','-o','/dev/null','-q', 'hpc-queue1', '-J', 'nf-task_1', '-M', '4000' ,'-R' ,'rusage[mem=4000] select[mem>4000]', '--X', 'abc', './job.sh']
        '8' | null | ['bsub','-cwd','/xxx','-o','/dev/null','-q', 'hpc-queue1', '-n', '8', '-R', '"span[hosts=1]"', '-J', 'nf-task_1', '-M', '4000' ,'-R' ,'rusage[mem=4000] select[mem>4000]', '--X', 'abc', './job.sh']
        '8' | 'smp' | ['bsub','-cwd','/xxx','-o','/dev/null','-q', 'hpc-queue1', '-n', '8', '-R', '"span[hosts=1]"', '-J', 'nf-task_1', '-M', '4000' ,'-R' ,'rusage[mem=4000] select[mem>4000]', '--X', 'abc', './job.sh']
        '8' | 'mpi' | ['bsub','-cwd','/xxx','-o','/dev/null','-q', 'hpc-queue1',  '-R', '"span[hosts=8]"', '-J', 'nf-task_1', '-M', '4000' ,'-R' ,'rusage[mem=4000] select[mem>4000]', '--X', 'abc', './job.sh']
    }


    def testParseJobId() {

        when:
        // executor stub object
        def executor = [:] as LsfExecutor
        then:
        executor.parseJobId( 'Job <2329803> is submitted to default queue <research-rh6>.' ) == '2329803'

    }

    def testKillCommand() {
        when:
        // executor stub object
        def executor = [:] as LsfExecutor
        then:
        executor.killTaskCommand('12345').join(' ') == 'bkill 12345'

    }

    def testQstatCommand() {

        setup:
        def executor = [:] as LsfExecutor
        def text =
                """
                6795348,RUN,Feb 17 13:26
                6795349,RUN,Feb 17 13:26
                6795351,PEND,Feb 17 13:26
                6795353,PSUSP,Feb 17 13:26
                6795354,EXIT,Feb 17 13:26
                """.stripIndent().trim()


        when:
        def result = executor.parseQueueStatus(text)
        then:
        result.size() == 5
        result['6795348'] == AbstractGridExecutor.QueueStatus.RUNNING
        result['6795349'] == AbstractGridExecutor.QueueStatus.RUNNING
        result['6795351'] == AbstractGridExecutor.QueueStatus.PENDING
        result['6795353'] == AbstractGridExecutor.QueueStatus.HOLD
        result['6795354'] == AbstractGridExecutor.QueueStatus.ERROR

    }


    def testQueueStatusCommand() {

        setup:
        def executor = [:] as LsfExecutor

        expect:
        executor.queueStatusCommand(null) == ['bjobs', '-o',  'JOBID STAT SUBMIT_TIME delimiter=\',\'', '-noheader']
        executor.queueStatusCommand('long') == ['bjobs', '-o',  'JOBID STAT SUBMIT_TIME delimiter=\',\'', '-noheader', '-q', 'long']

    }

}
