
const size = 1000;
var array = new Array(size);


array.fill(Math.random());
const rand = () => String(Math.floor(Math.random() * size));


for (var i = 0;i<array.length;i++){
    array[i] = rand();
}


function bubbleSort(arr) {

    let n = arr.length;


    for (let i = 0; i < n; i++) {

        let swapped = false;


        for (let j = 0; j < n - i - 1; j++) {

            if (arr[j] > arr[j + 1]) {
                let tmp = arr[j];
                arr[j] = arr[j + 1];
                arr[j + 1] = tmp;
                swapped = true;
            }
        }


        if (!swapped) {
            break;
        }
    }

    return;
}

var start = new Date();
bubbleSort(array);
var stop = new Date();
var diff = stop - start;
console.log(">>>>>>>>>>>> bubblesort end: time = ",diff," ms");




